package com.example.events.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper;

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
            // payload는 이미 JSON 문자열이라 그대로 보내면 JsonSerializer가 한 번 더 감싼다("{\"...\"}").
            // JsonNode로 넘겨 원본 JSON 그대로 전송 (이벤트 클래스로 역직렬화하지 않으므로 원본 보존)
            JsonNode payload = objectMapper.readTree(event.getPayload());
            kafkaTemplate.send(dlqTopic, event.getId().toString(), payload)
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
            case "CHAT" -> "chat-events.DLQ";
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 AggregateType: " + event.getAggregateType());
        };
    }
}
