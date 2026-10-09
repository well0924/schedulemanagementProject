package com.example.kafka;

import com.example.apiclient.config.kafka.KafkaConsumerConfig;
import com.example.events.enums.ScheduleActionType;
import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import com.example.inbound.consumer.member.DlqMemberSignRetryConsumer;
import com.example.inbound.consumer.member.MemberSignUpRetryTopicConsumer;
import com.example.inbound.consumer.schedule.DlqNotificationRetryConsumer;
import com.example.inbound.consumer.schedule.NotificationRetryTopicConsumer;
import com.example.inbound.consumer.slack.SlackNotifier;
import com.example.notification.service.FailedMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.AcknowledgingConsumerAwareMessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// containerFactory를 지정하지 않은 DLQ·재시도 리스너가 운영과 같은 기본 팩토리 설정
// (application-kafka.yml: StringDeserializer, ack-mode MANUAL)에서 실제로 동작하는지 검증한다.
// 브로커 없이 리스너 어댑터에 레코드를 직접 넣어 메시지 변환 → 컨슈머 호출 → 커밋까지 확인.
@SpringBootTest(classes = KafkaRetryConsumerWiringTest.TestConfig.class, properties = {
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.listener.auto-startup=false"
})
@ActiveProfiles("kafka")
class KafkaRetryConsumerWiringTest {

    @Configuration
    @ImportAutoConfiguration({KafkaAutoConfiguration.class, JacksonAutoConfiguration.class})
    @Import({KafkaConsumerConfig.class,
            DlqMemberSignRetryConsumer.class, MemberSignUpRetryTopicConsumer.class,
            DlqNotificationRetryConsumer.class, NotificationRetryTopicConsumer.class})
    static class TestConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @MockBean(name = "notificationKafkaTemplate")
    KafkaTemplate<String, NotificationEvents> notificationKafkaTemplate;
    @MockBean(name = "memberKafkaTemplate")
    KafkaTemplate<String, MemberSignUpKafkaEvent> memberKafkaTemplate;
    @MockBean(name = "objectKafkaTemplate")
    KafkaTemplate<String, Object> objectKafkaTemplate;
    @MockBean
    FailedMessageService failedMessageService;
    @MockBean
    SlackNotifier slackNotifier;

    @Autowired
    KafkaListenerEndpointRegistry registry;
    @Autowired
    ObjectMapper objectMapper;

    Acknowledgment ack;

    @BeforeEach
    void setUp() {
        ack = mock(Acknowledgment.class);
    }

    @SuppressWarnings("unchecked")
    void deliver(String topic, String value) {
        MessageListenerContainer container = registry.getListenerContainers().stream()
                .filter(c -> Arrays.asList(c.getContainerProperties().getTopics()).contains(topic))
                .findFirst()
                .orElseThrow(() -> new AssertionError("리스너 없음: " + topic));
        assertThat(container.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.MANUAL);

        ((AcknowledgingConsumerAwareMessageListener<String, String>) container.getContainerProperties().getMessageListener())
                .onMessage(new ConsumerRecord<>(topic, 0, 0L, "key", value), ack, null);
    }

    String memberJson() throws Exception {
        return objectMapper.writeValueAsString(MemberSignUpKafkaEvent.of(1L, "tester", "tester@example.com"));
    }

    String notificationJson() throws Exception {
        return objectMapper.writeValueAsString(NotificationEvents.builder()
                .receiverId(1L).scheduleId(2L).message("m")
                .notificationType(ScheduleActionType.SCHEDULE_CREATED).build());
    }

    @Test
    @DisplayName("회원가입 DLQ: 문자열 메시지를 역직렬화해 실패 내역 저장 후 커밋")
    void memberDlq_savesAndAcks() throws Exception {
        deliver("member-signup-events.DLQ", memberJson());

        verify(failedMessageService).createFailMessage(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("회원가입 DLQ: 이미 저장된 이벤트면 저장하지 않고 커밋만")
    void memberDlq_duplicate_acksWithoutSave() throws Exception {
        when(failedMessageService.isAlreadyRecorded(anyString())).thenReturn(true);

        deliver("member-signup-events.DLQ", memberJson());

        verify(failedMessageService, never()).createFailMessage(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("회원가입 재시도(5s): 원 토픽으로 재전송 후 커밋")
    void memberRetry_resendsAndAcks() throws Exception {
        deliver("member-signup.retry.5s", memberJson());

        verify(memberKafkaTemplate).send(eq("member-signup-events"), any(), any(MemberSignUpKafkaEvent.class));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("회원가입 재시도: 역직렬화 불가 메시지는 재전송하지 않고 커밋")
    void memberRetry_malformed_acksWithoutResend() {
        deliver("member-signup.retry.5s", "{ not json");

        verify(memberKafkaTemplate, never()).send(anyString(), any(), any(MemberSignUpKafkaEvent.class));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("알림 DLQ: 실패 내역 저장 후 커밋")
    void notificationDlq_savesAndAcks() throws Exception {
        deliver("notification-events.DLQ", notificationJson());

        verify(failedMessageService).createFailMessage(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("알림 재시도(5s): 원 토픽으로 재전송 후 커밋")
    void notificationRetry_resendsAndAcks() throws Exception {
        deliver("notification-events.retry.5s", notificationJson());

        verify(notificationKafkaTemplate).send(eq("notification-events"), any(), any(NotificationEvents.class));
        verify(ack).acknowledge();
    }
}
