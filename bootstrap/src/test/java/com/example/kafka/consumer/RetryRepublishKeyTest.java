package com.example.kafka.consumer;

import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import com.example.inbound.consumer.member.MemberSignUpRetryTopicConsumer;
import com.example.inbound.consumer.schedule.NotificationRetryTopicConsumer;
import com.example.inbound.consumer.slack.SlackNotifier;
import com.example.notification.service.FailedMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// 재처리 토픽에서 원본 토픽으로 다시 발행할 때 키를 붙이는지 검증.
// 예전엔 키 없이 보내 같은 일정·회원의 메시지가 원래와 다른 파티션으로 갈 수 있었다.
class RetryRepublishKeyTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("알림 재처리 재발행은 일정 ID를 키로 보낸다 (Outbox 발행 키와 같음)")
    @SuppressWarnings("unchecked")
    void notificationRetry_usesScheduleIdAsKey() throws Exception {
        KafkaTemplate<String, NotificationEvents> template = mock(KafkaTemplate.class);
        NotificationRetryTopicConsumer consumer = new NotificationRetryTopicConsumer(template, objectMapper,
                mock(SlackNotifier.class), new SimpleMeterRegistry(), mock(FailedMessageService.class));
        NotificationEvents event = NotificationEvents.builder()
                .receiverId(1L).scheduleId(42L).message("알림")
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .notificationChannel(NotificationChannel.WEB)
                .createdTime(LocalDateTime.now())
                .build();

        consumer.retry5s(objectMapper.writeValueAsString(event), mock(Acknowledgment.class));

        verify(template).send(eq("notification-events"), eq("42"), any(NotificationEvents.class));
    }

    @Test
    @DisplayName("회원가입 재처리 재발행은 회원 ID를 키로 보낸다")
    @SuppressWarnings("unchecked")
    void memberRetry_usesMemberIdAsKey() throws Exception {
        KafkaTemplate<String, MemberSignUpKafkaEvent> template = mock(KafkaTemplate.class);
        MemberSignUpRetryTopicConsumer consumer = new MemberSignUpRetryTopicConsumer(template, new SimpleMeterRegistry(),
                mock(SlackNotifier.class), mock(FailedMessageService.class), objectMapper);
        MemberSignUpKafkaEvent event = MemberSignUpKafkaEvent.of(7L, "tester", "user@example.com");

        consumer.retry5s(objectMapper.writeValueAsString(event), mock(Acknowledgment.class));

        verify(template).send(eq("member-signup-events"), eq("7"), any(MemberSignUpKafkaEvent.class));
    }

    @Test
    @DisplayName("키 계산 메서드는 JSON에 들어가지 않는다 (getter가 아님)")
    void partitionKey_isNotSerialized() throws Exception {
        NotificationEvents event = NotificationEvents.builder().scheduleId(42L).build();

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).doesNotContain("partitionKey");
        assertThat(event.partitionKey()).isEqualTo("42");
        assertThat(NotificationEvents.builder().build().partitionKey()).isNull();
    }
}
