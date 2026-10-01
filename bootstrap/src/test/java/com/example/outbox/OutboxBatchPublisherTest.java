package com.example.outbox;

import com.example.events.outbox.OutboxDlqProcessor;
import com.example.events.outbox.OutboxEventEntity;
import com.example.events.outbox.OutboxEventPublisher;
import com.example.events.outbox.OutboxEventSender;
import com.example.events.outbox.OutboxEventService;
import com.example.events.outbox.OutboxPayloadResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

// 배치 선점 발행기: 선점 1번 → 전부 전송 → 성공/실패를 한꺼번에 반영하는지 확인 (브로커·DB 없이)
class OutboxBatchPublisherTest {

    private final OutboxEventService service = mock(OutboxEventService.class);
    private final OutboxDlqProcessor dlqProcessor = mock(OutboxDlqProcessor.class);
    private final OutboxEventSender sender = mock(OutboxEventSender.class);
    private final OutboxEventPublisher publisher = new OutboxEventPublisher(service, dlqProcessor, sender);

    private OutboxEventEntity event(String id) {
        return OutboxEventEntity.builder()
                .id(id).aggregateType("SCHEDULE").aggregateId("schedule-" + id).eventType("SCHEDULE_CREATED")
                .payload("{}").sent(false).retryCount(1).createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("선점한 이벤트가 없으면 아무것도 하지 않는다")
    void nothingClaimed() {
        given(service.claimBatch(anyString(), anyInt(), any())).willReturn(List.of());

        publisher.publishOutboxEvents();

        verifyNoInteractions(sender, dlqProcessor);
        verify(service, never()).markSent(any());
    }

    @Test
    @DisplayName("성공한 건은 한 번에 발행 완료로, 실패한 건은 한 번에 선점 해제하고 DLQ 판단에 넘긴다")
    void successAndFailureAreReflectedInBatch() throws Exception {
        OutboxEventEntity a = event("a"), b = event("b"), c = event("c"), d = event("d");
        given(service.claimBatch(anyString(), anyInt(), any())).willReturn(List.of(a, b, c, d));
        given(sender.sendAsync(a)).willReturn(CompletableFuture.completedFuture(null));
        given(sender.sendAsync(b)).willReturn(CompletableFuture.completedFuture(null));
        given(sender.sendAsync(c)).willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        given(sender.sendAsync(d)).willThrow(new IOException("payload 변환 실패")); // 전송 전 실패

        publisher.publishOutboxEvents();

        // DB 반영은 성공·실패 각각 한 번씩만
        verify(service, times(1)).markSent(List.of("a", "b"));
        verify(service, times(1)).releaseClaims(argThat(ids -> ids.size() == 2 && ids.containsAll(List.of("c", "d"))));
        verify(dlqProcessor, times(1)).process(argThat(list -> list.size() == 2 && list.containsAll(List.of(c, d))));
    }

    @Test
    @DisplayName("한 번의 실행은 배치 선점을 한 번만 한다 (건별 선점 없음)")
    void claimsOncePerRun() throws Exception {
        given(service.claimBatch(anyString(), anyInt(), any())).willReturn(List.of(event("a"), event("b")));
        given(sender.sendAsync(any())).willReturn(CompletableFuture.completedFuture(null));

        publisher.publishOutboxEvents();

        verify(service, times(1)).claimBatch(anyString(), eq(200), any());
        verify(service, never()).tryLockEvent(anyString());
    }

    @Test
    @DisplayName("Kafka 메시지 키는 aggregateId라 같은 대상의 이벤트가 같은 파티션으로 간다")
    @SuppressWarnings("unchecked")
    void sender_usesAggregateIdAsKey() throws Exception {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        OutboxPayloadResolver resolver = mock(OutboxPayloadResolver.class);
        Object payload = new Object();
        OutboxEventEntity e = event("x");
        given(resolver.resolve(e)).willReturn(payload);
        given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(new CompletableFuture<>());

        new OutboxEventSender(kafkaTemplate, resolver).sendAsync(e);

        verify(kafkaTemplate).send("notification-events", "schedule-x", payload);
    }
}
