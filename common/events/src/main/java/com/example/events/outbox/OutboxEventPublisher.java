package com.example.events.outbox;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Slf4j
@Component
@AllArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventService outboxEventService;

    private final OutboxDlqProcessor outboxDlqProcessor;

    private final OutboxEventSender outboxEventSender;

    /**
     * 1초마다 Outbox의 미전송 이벤트를 조회하여 발행한다.
     * ShedLock으로 다중 인스턴스 환경에서 단일 실행을 보장한다.
     * (2026-09-14) 기존 fixedDelay=3000/limit=100 → 최대 33.3건/초 드레인 한계였는데,
     * 스케줄 생성 처리량이 최대 249건/초까지 나오면서 Outbox 백로그가 급증하는 게 확인돼
     * 1000ms/200건(최대 200건/초)으로 1차 튜닝. lockAtLeastFor도 fixedDelay보다 작게 맞춤.
     */
    @Timed(value = "outbox.publish.duration", description = "Outbox Kafka 발행 처리 시간")
    @Counted(value = "outbox.publish.count", description = "Outbox Kafka 발행 실행 횟수")
    @Scheduled(fixedDelay = 1000)
    @SchedulerLock(name = "OutboxPublisherLock", lockAtMostFor = "PT10M", lockAtLeastFor = "PT500MS")
    public void publishOutboxEvents() {
        // 전송되지 않은 이벤트를 생성순으로 200건씩 가져와 순차 발행
        List<OutboxEventEntity> events = outboxEventService.getPendingEvents(200);

        for (OutboxEventEntity event : events) {
            if (outboxEventService.tryLockEvent(event.getId())) {
                try {
                    outboxEventSender.send(event);
                } catch (Exception e) {
                    event.increaseRetryCount();
                    log.error("Kafka 발행 실패 - id={}, error={}", event.getId(), e.getMessage());
                    // 실패하면 그대로 두면 됨 → 재시도됨
                }
            }
        }
        outboxDlqProcessor.process(events);
    }

}
