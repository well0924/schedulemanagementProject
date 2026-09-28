package com.example.kafka;

import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import com.example.events.outbox.OutboxDlqProcessor;
import com.example.events.outbox.OutboxEventEntity;
import com.example.events.outbox.OutboxEventRepository;
import com.example.events.spring.ChatCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Kafka 이벤트 직렬화/역직렬화 회귀 테스트
// - @SuperBuilder만 있고 기본 생성자가 없으면 Jackson이 역직렬화하지 못한다 (@Jacksonized 필요)
// - OutboxDlqProcessor가 JSON 문자열 payload를 이중 직렬화하지 않아야 한다
class KafkaEventSerializationTest {

    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("NotificationEvents JSON 왕복")
    void notificationEvents_roundTrip() throws Exception {
        NotificationEvents original = NotificationEvents.builder()
                .receiverId(1L)
                .scheduleId(2L)
                .message("📅 일정이 생성되었습니다: 회의")
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .notificationChannel(NotificationChannel.WEB)
                .forceSend(true)
                .scheduleAt(LocalDateTime.of(2026, 9, 28, 9, 55))
                .createdTime(LocalDateTime.of(2026, 9, 28, 9, 0))
                .build();

        String json = objectMapper.writeValueAsString(original);
        NotificationEvents restored = objectMapper.readValue(json, NotificationEvents.class);

        assertThat(restored).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    @DisplayName("MemberSignUpKafkaEvent JSON 왕복")
    void memberSignUpEvent_roundTrip() throws Exception {
        MemberSignUpKafkaEvent original = MemberSignUpKafkaEvent.of(1L, "tester", "tester@example.com");

        String json = objectMapper.writeValueAsString(original);
        MemberSignUpKafkaEvent restored = objectMapper.readValue(json, MemberSignUpKafkaEvent.class);

        assertThat(restored).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    @DisplayName("ChatCompletedEvent JSON 왕복")
    void chatCompletedEvent_roundTrip() throws Exception {
        ChatCompletedEvent original = ChatCompletedEvent.builder()
                .memberId(1L)
                .userMessage("내일 일정 추천해줘")
                .assistantResponse("오전 10시에 회의를 추천합니다.")
                .createdAt(LocalDateTime.of(2026, 9, 28, 9, 0))
                .build();

        String json = objectMapper.writeValueAsString(original);
        ChatCompletedEvent restored = objectMapper.readValue(json, ChatCompletedEvent.class);

        assertThat(restored).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    @DisplayName("eventId가 없는 JSON도 역직렬화되고 기본 eventId가 채워진다")
    void missingEventId_usesDefault() throws Exception {
        NotificationEvents restored = objectMapper.readValue("{\"receiverId\":1}", NotificationEvents.class);

        assertThat(restored.getReceiverId()).isEqualTo(1L);
        assertThat(restored.getEventId()).isNotBlank();
    }

    @Test
    @DisplayName("OutboxDlqProcessor → DLQ 전송값이 이중 직렬화되지 않고 원본 JSON 그대로 나간다")
    @SuppressWarnings("unchecked")
    void outboxDlq_sendsOriginalJson() throws Exception {
        NotificationEvents event = NotificationEvents.builder()
                .receiverId(1L)
                .scheduleId(2L)
                .message("m")
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .build();
        String payload = objectMapper.writeValueAsString(event);

        OutboxEventEntity outbox = OutboxEventEntity.builder()
                .id("outbox-1")
                .aggregateType("SCHEDULE")
                .aggregateId("2")
                .eventType("SCHEDULE_CREATED")
                .payload(payload)
                .sent(false)
                .retryCount(6)
                .createdAt(LocalDateTime.now())
                .build();

        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(new CompletableFuture<SendResult<String, Object>>());
        OutboxDlqProcessor processor =
                new OutboxDlqProcessor(kafkaTemplate, mock(OutboxEventRepository.class), objectMapper);

        processor.process(List.of(outbox));

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("notification-events.DLQ"), eq("outbox-1"), sent.capture());

        // 실제 운영과 같은 JsonSerializer로 직렬화한 결과(= Kafka에 실리는 값)를 DLQ 컨슈머처럼 읽는다
        try (JsonSerializer<Object> serializer = new JsonSerializer<>(objectMapper)) {
            String onWire = new String(
                    serializer.serialize("notification-events.DLQ", sent.getValue()), StandardCharsets.UTF_8);

            assertThat(onWire).startsWith("{");  // 문자열로 한 번 더 감싸면 "\"{...}\"" 형태가 된다
            NotificationEvents restored = objectMapper.readValue(onWire, NotificationEvents.class);
            assertThat(restored.getEventId()).isEqualTo(event.getEventId());
            assertThat(restored.getReceiverId()).isEqualTo(1L);
        }
    }
}
