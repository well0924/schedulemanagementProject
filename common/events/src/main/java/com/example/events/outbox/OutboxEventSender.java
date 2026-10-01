package com.example.events.outbox;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@AllArgsConstructor
public class OutboxEventSender {

    @Qualifier("objectKafkaTemplate")
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final OutboxPayloadResolver payloadResolver;

    /**
     * Outbox 이벤트를 Kafka로 비동기 전송하고, 전송 결과 Future를 돌려준다.
     *
     * 예전에는 whenComplete 콜백 안에서 건마다 DB(sent=true / retry_count+1)를 갱신했다.
     * 이 콜백은 Kafka 프로듀서의 I/O 스레드에서 돌기 때문에 DB 왕복이 전송 자체를 늦췄고,
     * 건마다 트랜잭션이 생겨 발행 처리량이 약 50 events/s에서 막혔다(490VU 측정).
     * 이제 상태 갱신은 발행기가 배치 단위로 한 번에 한다.
     *
     * 메시지 키는 aggregateId(예: 일정 ID)다. 같은 대상의 이벤트는 같은 파티션으로 가서
     * 파티션 안에서 순서가 지켜진다(예: 같은 일정의 생성 → 수정).
     */
    public CompletableFuture<SendResult<String, Object>> sendAsync(OutboxEventEntity event) throws IOException {
        Object payload = payloadResolver.resolve(event);
        return kafkaTemplate.send(event.resolveTopic(), event.getAggregateId(), payload);
    }
}
