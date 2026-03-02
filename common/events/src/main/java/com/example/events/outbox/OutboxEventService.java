package com.example.events.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

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
            throw new RuntimeException("Outbox 이벤트 Bulk 직렬화 실패", e);
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
            throw new RuntimeException("Outbox 이벤트 직렬화 실패", e);
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
}
