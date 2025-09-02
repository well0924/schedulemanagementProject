package com.example.events.outbox;

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
    @Transactional
    public void publishOutboxEvents() {
        List<OutboxEventEntity> events = repository.findTop100BySentFalseOrderByCreatedAtAsc();

        for (OutboxEventEntity event : events) {
            try {
                String topic = event.resolveTopic();
                log.info(topic);
                Object payload = objectMapper.readValue(event.getPayload(), resolveEventClass(event));
                // eventId 에 따라 분기처리
                if (payload instanceof NotificationEvents notificationEvent) {
                    if (notificationEvent.getEventId() == null) { // Outbox PK → eventId
                        notificationEvent.setEventId(event.getId());
                    }
                    kafkaTemplate.send(topic, notificationEvent);
                } else if (payload instanceof MemberSignUpKafkaEvent signUpEvent) {
                    if(signUpEvent.getEventId() == null) {
                        signUpEvent.setEventId(event.getId()); // Outbox PK → eventId
                    }
                    kafkaTemplate.send(topic, signUpEvent);
                }
                event.markSent();
                log.info("Kafka 발행 성공 - type={}, id={}", event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                event.increaseRetryCount();
                log.error("Kafka 발행 실패 - id={}, error={}", event.getId(), e.getMessage());
                // 실패하면 그대로 두면 됨 → 재시도됨
            }
        }
        repository.saveAll(events); // 전송 상태 반영
    }

    private Class<?> resolveEventClass(OutboxEventEntity event) {
        return switch (event.getAggregateType()) {
            case "MEMBER" -> MemberSignUpKafkaEvent.class;
            case "SCHEDULE" -> NotificationEvents.class;
            default -> throw new IllegalArgumentException("지원하지 않는 AggregateType: " + event.getAggregateType());
        };
    }
}
