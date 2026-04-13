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

    private final OutboxEventRepository repository;
    private final OutboxPayloadResolver payloadResolver;

    /**
     * Outbox 이벤트를 Kafka로 비동기 발행한다.
     * payload 생성 후 토픽으로 전송하고 결과를 즉시 DB에 반영한다.
     */
    public void send(OutboxEventEntity event) throws IOException {
        Object payload = payloadResolver.resolve(event);
        String topic = event.resolveTopic();

        kafkaTemplate.send(topic, payload)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        handleSuccess(event, result);
                    } else {
                        handleFailure(event, topic, ex);
                    }
                });
    }

    /**
     * Kafka 발행 성공 시 sent=true로 즉시 저장한다.
     * whenComplete 콜백 안에서 저장하여 비동기 순서를 보장한다.
     */
    private void handleSuccess(OutboxEventEntity event,
                               org.springframework.kafka.support.SendResult<String, Object> result) {
        event.markSent();
        repository.save(event);
        log.info("Kafka 발행 성공 - topic={}, partition={}, offset={}, eventId={}",
                result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset(),
                event.getId());
    }

    /**
     * Kafka 발행 실패 시 retryCount를 증가시키고 즉시 저장한다.
     * 재시도는 다음 폴링 주기에 자동으로 수행된다.
     */
    private void handleFailure(OutboxEventEntity event, String topic, Throwable ex) {
        event.increaseRetryCount();
        repository.save(event);
        log.error("Kafka 발행 실패 - topic={}, eventId={}, error={}",
                topic, event.getId(), ex.getMessage(), ex);
    }
}
