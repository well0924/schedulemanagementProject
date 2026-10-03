package com.example.events.outbox;

import com.example.exception.dto.ErrorCode;
import com.example.exception.global.CustomExceptionHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // outbox.pending.count: sent=false 백로그 크기. poller(fixedDelay=3000, limit=100)가
    // 실제 쓰기 속도를 못 따라가는지 확인하려고 추가 (2026-09-10, CAS 회귀 재테스트용)
    @PostConstruct
    public void registerPendingBacklogGauge() {
        Gauge.builder("outbox.pending.count", outboxEventRepository, OutboxEventRepository::countBySentFalse)
                .description("Outbox 미발행(sent=false) 이벤트 백로그 크기")
                .register(meterRegistry);
    }

    @Transactional(readOnly = true)
    public List<OutboxEventEntity> getPendingEvents(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return outboxEventRepository.findPendingEvents(pageable);
    }

    // 반복 일정 수정/삭제 시 bulk 처리
    @Transactional
    public void saveAllEvents(List<Object> eventDtos, String aggregateType, List<String> aggregateIds, List<String> eventTypes) {
        if(eventDtos == null || eventDtos.isEmpty()) {
            return;
        }

        try {
            List<OutboxEventEntity> entities = new ArrayList<>();
            for (int i = 0; i < eventDtos.size(); i++) {
                String payload = objectMapper.writeValueAsString(eventDtos.get(i));

                OutboxEventEntity entity = OutboxEventEntity.builder()
                        .aggregateType(aggregateType)
                        .aggregateId(aggregateIds.get(i)) // 리스트에서 인덱스로 추출
                        .eventType(eventTypes.get(i))     // 리스트에서 인덱스로 추출
                        .payload(payload)
                        .retryCount(0)
                        .createdAt(LocalDateTime.now())
                        .sent(false)
                        .build();
                entities.add(entity);
            }
            outboxEventRepository.saveAll(entities);
            log.info("{}건의 Outbox 이벤트 Bulk 저장 완료", entities.size());
        } catch (JsonProcessingException e) {
            throw new CustomExceptionHandler(ErrorCode.EVENT_SERIALIZATION_ERROR);
        }
    }

    // REQUIRES_NEW 옵션은 호출 측 비즈니스 로직의 롤백 여부와 상관없이 로그성 이벤트를 남겨야 할 때 사용하거나,
    // 별도의 트랜잭션 격리가 필요할 때 선택적으로 사용
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveEvent(Object eventDto, String aggregateType, String aggregateId, String eventType) {
        try {
            log.info("save?");
            String payload = objectMapper.writeValueAsString(eventDto);
            log.info(payload);
            OutboxEventEntity entity = OutboxEventEntity.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payload)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .sent(false)
                    .build();
            log.info("outbox?:"+entity);
            outboxEventRepository.save(entity);

        } catch (JsonProcessingException e) {
            throw new CustomExceptionHandler(ErrorCode.EVENT_SERIALIZATION_ERROR);
        }
    }

    // 아웃박스에 저장이 된 내역을 주기적으로 일괄 삭제
    @Scheduled(cron = "0 0 1 * * ?") // 매일 새벽 1시에 실행
    @SchedulerLock(name = "OutboxCleanUpLock", lockAtMostFor = "PT10M", lockAtLeastFor = "PT2S")
    @Transactional
    public void cleanUpFinishedEvents() {
        // 발행 성공(sent=true)하고 생성된 지 3일이 지난 이벤트 삭제
        LocalDateTime threshold = LocalDateTime.now().minusDays(3);
        int deletedCount = outboxEventRepository.deleteBySentTrueAndCreatedAtBefore(threshold);
        log.info("Outbox 청소 완료: {}건의 오래된 이벤트 삭제", deletedCount);
    }

    @Transactional
    public void updateSentStatus(String id) {
        outboxEventRepository.markAsSent(id, LocalDateTime.now());
    }

    @Transactional
    public void incrementRetryCount(String id) {
        outboxEventRepository.incrementRetryCount(id);
    }

    @Transactional
    public boolean tryLockEvent(String id) {
        // 1을 반환하면 락 성공, 0이면 이미 다른 곳에서 선점 중
        int updatedRows = outboxEventRepository.tryLockAndIncrement(id);
        return updatedRows > 0;
    }

    // ---- 배치 선점 방식 (2026-10-02) ----
    // 각 단계는 짧은 트랜잭션으로 끝내고 커밋한다. Kafka 전송은 트랜잭션 밖에서 하므로
    // 발행 중에 DB 커넥션이나 행 락을 잡고 있지 않는다.

    /**
     * 미발행 이벤트를 최대 limit건 선점하고, 선점한 행을 돌려준다.
     * staleAfter보다 오래 선점된 채 남은 행(발행 도중 서버가 죽은 경우)도 다시 가져온다.
     *
     * REPEATABLE READ에서는 이 범위 UPDATE가 스캔 범위와 그 끝의 간격까지 잠가,
     * 새 Outbox 행이 들어가는 자리(미발행 구간 끝)의 INSERT를 막을 수 있다.
     * READ COMMITTED는 간격 잠금을 걸지 않고, 커밋되지 않은 행은 기다리지 않고 건너뛴다 (2026-10-03).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public List<OutboxEventEntity> claimBatch(String claimId, int limit, Duration staleAfter) {
        LocalDateTime now = LocalDateTime.now();
        int claimed = outboxEventRepository.claimBatch(claimId, now, now.minus(staleAfter), limit);
        if (claimed == 0) {
            return List.of();
        }
        return outboxEventRepository.findByClaimIdOrderByCreatedAtAsc(claimId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(List<String> ids) {
        if (ids.isEmpty()) return;
        outboxEventRepository.markSentByIds(ids, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaims(List<String> ids) {
        if (ids.isEmpty()) return;
        outboxEventRepository.releaseClaims(ids);
    }
}
