package com.example.events.outbox;

import com.example.events.kafka.BaseKafkaEvent;
import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@AllArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository repository;

    private final OutboxDlqProcessor outboxDlqProcessor;

    private final OutboxEventSender outboxEventSender;

    /**
     * 3초마다 Outbox의 미전송 이벤트를 조회하여 발행한다.
     * ShedLock으로 다중 인스턴스 환경에서 단일 실행을 보장한다.
     */
    @Timed(value = "outbox.publish.duration", description = "Outbox Kafka 발행 처리 시간")
    @Counted(value = "outbox.publish.count", description = "Outbox Kafka 발행 실행 횟수")
    @Scheduled(fixedDelay = 3000) //3초마다 실행
    @SchedulerLock(name = "OutboxPublisherLock", lockAtMostFor = "PT10M", lockAtLeastFor = "PT2S")
    public void publishOutboxEvents() {
        // 전송되지 않은 이벤트를 생성순으로 100건씩 가져와 순차 발행
        List<OutboxEventEntity> events = repository.findTop100BySentFalseOrderByCreatedAtAsc();

        for (OutboxEventEntity event : events) {
            try {
                outboxEventSender.send(event);
            } catch (Exception e) {
                event.increaseRetryCount();
                log.error("Kafka 발행 실패 - id={}, error={}", event.getId(), e.getMessage());
                // 실패하면 그대로 두면 됨 → 재시도됨
            }
        }
        outboxDlqProcessor.process(events);
    }

}
