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

    @Qualifier("objectKafkaTemplate")
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final ObjectMapper objectMapper;

    @Timed(value = "outbox.publish.duration", description = "Outbox Kafka 발행 처리 시간")
    @Counted(value = "outbox.publish.count", description = "Outbox Kafka 발행 실행 횟수")
    @Scheduled(fixedDelay = 3000) //3초마다 실행
    @SchedulerLock(name = "OutboxPublisherLock", lockAtMostFor = "PT10M", lockAtLeastFor = "PT2S")
    public void publishOutboxEvents() {
        List<OutboxEventEntity> events = repository.findTop100BySentFalseOrderByCreatedAtAsc();

        for (OutboxEventEntity event : events) {
            try {
                publishSingleEvent(event);
            } catch (Exception e) {
                event.increaseRetryCount();
                log.error("Kafka 발행 실패 - id={}, error={}", event.getId(), e.getMessage());
                // 실패하면 그대로 두면 됨 → 재시도됨
            }
        }
        updateEvents(events); // 전송 상태 반영
    }

    // 이벤트를 Kafka로 발행한다.
    private void publishSingleEvent(OutboxEventEntity event) throws IOException {
        String topic = event.resolveTopic();
        Object payload = objectMapper.readValue(event.getPayload(), resolveEventClass(event));

        // eventId 주입 (Outbox PK → Kafka eventId)
        if (payload instanceof BaseKafkaEvent baseEvent) {
            if(baseEvent.getEventId() == null || baseEvent.getEventId().isBlank()) {
                baseEvent.setEventId(event.getId());
                log.info("eventId setting:"+baseEvent.getEventId());
            }
        }

        kafkaTemplate.send(topic, payload)
                .whenComplete((result, ex) -> {
            if (ex == null) {
                // sent를 true로 변경
                event.markSent();
                log.info("Kafka 발행 성공 - topic={}, partition={}, offset={}, eventId={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        event.getId());
            } else {
                // 재시도 횟수 증가
                event.increaseRetryCount();
                log.error("Kafka 발행 실패 - topic={}, eventId={}, error={}",
                        topic, event.getId(), ex.getMessage(), ex);
            }
        });
    }

    // Outbox 이벤트 상태를 DB에 반영한다.
    // 5회 이상 실패를 하면 sent=false → DLQ 토픽으로 이동
    // DLQ 전송 성공 시 Outbox에서 삭제하여 중복 발행 방지
    @Transactional
    public void updateEvents(List<OutboxEventEntity> events) {
        for (OutboxEventEntity event : events) {
            if (event.getRetryCount() > 5 && event.getOutboxStatus() == OutboxStatus.FAILED) {
                event.markFailed();
                log.warn("Outbox 이벤트 영구 실패 처리: eventId={}", event.getId());
            }
        }
        repository.saveAll(events);
    }

    private Class<?> resolveEventClass(OutboxEventEntity event) {
        return switch (event.getAggregateType()) {
            case "MEMBER" -> MemberSignUpKafkaEvent.class;
            case "SCHEDULE" -> NotificationEvents.class;
            default -> throw new IllegalArgumentException("지원하지 않는 AggregateType: " + event.getAggregateType());
        };
    }
}
