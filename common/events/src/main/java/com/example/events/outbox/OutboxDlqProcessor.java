package com.example.events.outbox;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@AllArgsConstructor
public class OutboxDlqProcessor {

    @Qualifier("objectKafkaTemplate")
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final OutboxEventRepository repository;

    /**
     * retryCount > 5 이고 sent=false인 이벤트를 DLQ로 이동한다.
     */
    @Transactional
    public void process(List<OutboxEventEntity> events) {
        events.stream()
                .filter(event -> event.getRetryCount() > 5 && !event.getSent())
                .forEach(this::sendToDlq);
    }

    /**
     * DLQ 토픽으로 이벤트를 전송하고 Outbox에서 삭제한다.
     * 삭제는 DLQ 전송 요청 후 즉시 수행하여 중복 발행을 방지한다.
     */
    private void sendToDlq(OutboxEventEntity event) {
        try {
            String dlqTopic = resolveDlqTopic(event);
            kafkaTemplate.send(dlqTopic, event.getId().toString(), event.getPayload())
                    .whenComplete((result, ex) -> handleDlqResult(event, dlqTopic, ex));
        } catch (Exception e) {
            log.error("DLQ 처리 중 예외 - eventId={}, error={}",
                    event.getId(), e.getMessage());
        }
    }

    /**
     * DLQ 전송 결과를 로깅한다.
     */
    private void handleDlqResult(OutboxEventEntity event, String dlqTopic, Throwable ex) {
        if (ex == null) {
            log.warn("DLQ 전송 성공 - eventId={}, dlqTopic={}",
                    event.getId(), dlqTopic);
            repository.delete(event);
        } else {
            log.error("DLQ 전송 실패 - eventId={}, dlqTopic={}, error={}",
                    event.getId(), dlqTopic, ex.getMessage(), ex);
        }
    }

    /**
     * aggregateType에 따라 DLQ 토픽명을 반환한다.
     */
    private String resolveDlqTopic(OutboxEventEntity event) {
        return switch (event.getAggregateType()) {
            case "MEMBER" -> "member-signup-events.DLQ";
            case "SCHEDULE" -> "notification-events.DLQ";
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 AggregateType: " + event.getAggregateType());
        };
    }
}
