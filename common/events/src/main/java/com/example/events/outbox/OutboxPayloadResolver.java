package com.example.events.outbox;

import com.example.events.kafka.BaseKafkaEvent;
import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@AllArgsConstructor
public class OutboxPayloadResolver {

    private final ObjectMapper objectMapper;

    /**
     * Outbox 이벤트의 payload를 역직렬화하고 eventId를 주입한다.
     * eventId가 없는 경우 Outbox PK를 eventId로 주입하여
     * 컨슈머의 멱등성 체크에 사용될 수 있도록 한다.
     */
    public Object resolve(OutboxEventEntity event) throws IOException {
        Object payload = objectMapper.readValue(
                event.getPayload(), resolveEventClass(event));

        if (payload instanceof BaseKafkaEvent baseEvent) {
            if (baseEvent.getEventId() == null || baseEvent.getEventId().isBlank()) {
                baseEvent.setEventId(event.getId());
                log.info("eventId setting: {}", baseEvent.getEventId());
            }
        }
        return payload;
    }

    /**
     * aggregateType에 따라 역직렬화할 이벤트 클래스를 반환한다.
     */
    private Class<?> resolveEventClass(OutboxEventEntity event) {
        return switch (event.getAggregateType()) {
            case "MEMBER" -> MemberSignUpKafkaEvent.class;
            case "SCHEDULE" -> NotificationEvents.class;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 AggregateType: " + event.getAggregateType());
        };
    }
}
