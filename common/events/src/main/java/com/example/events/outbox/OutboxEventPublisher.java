package com.example.events.outbox;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@AllArgsConstructor
public class OutboxEventPublisher {

    // 한 번에 선점할 건수와 실행 간격은 개선 전(200건 / 1초)과 같게 두어, 처리 방식만 바뀐 효과를 비교한다.
    static final int BATCH_SIZE = 200;
    // 선점한 채 이 시간이 지나도 발행 완료되지 않은 행은 서버가 죽은 것으로 보고 다시 선점한다.
    static final Duration STALE_CLAIM = Duration.ofSeconds(30);
    // 한 배치의 Kafka 전송 결과를 기다리는 최대 시간
    static final long SEND_TIMEOUT_MS = 10_000;

    private final OutboxEventService outboxEventService;

    private final OutboxDlqProcessor outboxDlqProcessor;

    private final OutboxEventSender outboxEventSender;

    /**
     * 1초마다 Outbox의 미발행 이벤트를 발행한다. ShedLock으로 다중 인스턴스에서 한 서버만 실행한다.
     *
     * (2026-10-02) 건별 처리 → 배치 처리로 변경
     * 예전: 200건을 조회한 뒤 건마다 CAS UPDATE(선점)로 트랜잭션 1번, 발행 성공 콜백에서 건마다 UPDATE 1번
     *       → 한 주기에 DB 왕복 약 400번, 490VU에서 발행 한계 약 50 events/s, 적체 최대 약 25,000건.
     * 지금: ① UPDATE 1번으로 최대 200건 선점(claim_id) ② 트랜잭션 밖에서 전부 전송 후 결과를 한꺼번에 대기
     *       ③ 성공은 UPDATE 1번으로 sent=true, 실패는 UPDATE 1번으로 선점 해제(다음 주기에 재시도)
     *       → 한 주기에 DB 왕복 3~4번.
     * 선점 단위가 배치가 되어도 "한 이벤트는 한 실행만 가져간다"는 보장은 그대로다.
     */
    @Timed(value = "outbox.publish.duration", description = "Outbox Kafka 발행 처리 시간")
    @Counted(value = "outbox.publish.count", description = "Outbox Kafka 발행 실행 횟수")
    @Scheduled(fixedDelay = 1000)
    @SchedulerLock(name = "OutboxPublisherLock", lockAtMostFor = "PT10M", lockAtLeastFor = "500")
    public void publishOutboxEvents() {
        long startedAt = System.currentTimeMillis();

        // 선점
        String claimId = UUID.randomUUID().toString();
        List<OutboxEventEntity> events = outboxEventService.claimBatch(claimId, BATCH_SIZE, STALE_CLAIM);
        if (events.isEmpty()) {
            return;
        }
        long claimedAt = System.currentTimeMillis();

        // 전송 (전부 보낸 뒤 결과를 한꺼번에 기다린다)
        Map<OutboxEventEntity, CompletableFuture<SendResult<String, Object>>> futures = new LinkedHashMap<>();
        List<OutboxEventEntity> failed = new ArrayList<>();
        for (OutboxEventEntity event : events) {
            try {
                futures.put(event, outboxEventSender.sendAsync(event));
            } catch (Exception e) {
                // 페이로드 변환 실패 등 전송 전에 실패한 경우
                log.error("Kafka 발행 준비 실패 - id={}, error={}", event.getId(), e.getMessage());
                failed.add(event);
            }
        }
        awaitAll(futures.values());

        List<String> succeededIds = new ArrayList<>();
        futures.forEach((event, future) -> {
            if (future.isDone() && !future.isCompletedExceptionally()) {
                succeededIds.add(event.getId());
            } else {
                // 실패했거나 시간 안에 끝나지 않은 건은 선점을 풀어 다음 주기에 다시 보낸다.
                // (늦게 성공한 건이 다시 나가더라도 컨슈머가 eventId로 중복을 걸러낸다)
                failed.add(event);
            }
        });
        long sentAt = System.currentTimeMillis();

        // 결과 반영
        outboxEventService.markSent(succeededIds);
        outboxEventService.releaseClaims(failed.stream().map(OutboxEventEntity::getId).toList());
        outboxDlqProcessor.process(failed); // 시도 횟수 초과(retry_count > 5)만 DLQ로 이동
        long finishedAt = System.currentTimeMillis();

        log.info("[Outbox 발행] claimed={}, sent={}, failed={}, claim={}ms, send={}ms, mark={}ms, total={}ms",
                events.size(), succeededIds.size(), failed.size(),
                claimedAt - startedAt, sentAt - claimedAt, finishedAt - sentAt, finishedAt - startedAt);
    }

    private void awaitAll(Collection<CompletableFuture<SendResult<String, Object>>> futures) {
        if (futures.isEmpty()) return;
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            // 일부 전송 실패: 건별 결과는 호출부에서 future 상태로 판단한다
        } catch (TimeoutException e) {
            log.warn("Kafka 전송 결과 대기 시간 초과({}ms) - 끝나지 않은 건은 다음 주기에 재시도", SEND_TIMEOUT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
