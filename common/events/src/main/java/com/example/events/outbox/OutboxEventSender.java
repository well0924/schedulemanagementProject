package com.example.events.outbox;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@AllArgsConstructor
public class OutboxEventSender {

    @Qualifier("objectKafkaTemplate")
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final OutboxEventService outboxEventService;
    private final OutboxPayloadResolver payloadResolver;

    /**
     * Outbox 이벤트를 Kafka로 비동기 발행한다.
     * payload 생성 후 토픽으로 전송하고 결과를 즉시 DB에 반영한다.
     */
    public void send(OutboxEventEntity event) throws IOException {
        Object payload = payloadResolver.resolve(event);
        String topic = event.resolveTopic();

        // 비동기 작업이 끝날 때까지 이 값의 무결성을 보장하기 위해서 final을 사용
        final String eventId = event.getId();

        kafkaTemplate.send(topic, payload)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        handleSuccess(eventId, result);
                    } else {
                        handleFailure(eventId, topic, ex);
                    }
                });
    }

    /**
     * Kafka 발행 성공 시 sent=true로 즉시 저장한다.
     * whenComplete 콜백 안에서 저장하여 비동기 순서를 보장한다.
     */
    private void handleSuccess(String eventId,
                               org.springframework.kafka.support.SendResult<String, Object> result) {

        outboxEventService.updateSentStatus(eventId);

        log.info("Kafka 발행 성공 - topic={}, partition={}, offset={}, eventId={}",
                result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset(),
                eventId);
    }

    /**
     * Kafka 발행 실패 시 retryCount를 증가시키고 즉시 저장한다.
     * 재시도는 다음 폴링 주기에 자동으로 수행된다.
     */
    private void handleFailure(String eventId, String topic, Throwable ex) {

        outboxEventService.incrementRetryCount(eventId);

        log.error("Kafka 발행 실패 - topic={}, eventId={}, error={}",
                topic, eventId, ex.getMessage(), ex);
    }
}
