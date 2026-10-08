package com.example.kafka.consumer;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.process.ProcessedEventService;
import com.example.inbound.consumer.member.MemberSignUpKafkaEventConsumer;
import com.example.notification.email.EmailService;
import com.example.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 회원가입 컨슈머의 실패 처리 검증.
// 예전 코드는 알림 저장 실패를 삼킨 채 처리 기록을 남기고, 일반 예외도 삼켜 재시도·DLQ로 가지 않았다.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberSignUpKafkaEventConsumerTest {

    @Mock EmailService emailService;
    @Mock NotificationService notificationService;
    @Mock ProcessedEventService processedEventService;
    @Mock SimpMessagingTemplate simpMessagingTemplate;
    @Mock Acknowledgment ack;
    @Mock PlatformTransactionManager transactionManager;

    MemberSignUpKafkaEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new MemberSignUpKafkaEventConsumer(emailService, notificationService,
                processedEventService, simpMessagingTemplate, objectMapper,
                new TransactionTemplate(transactionManager));
        when(processedEventService.isAlreadyProcessed(anyString(), anyString())).thenReturn(false);
    }

    @Test
    @DisplayName("알림 저장이 실패하면 예외를 전파하고, 처리 기록·환영 메일·ack를 남기지 않는다")
    void saveFails_propagatesWithoutSideEffects() throws Exception {
        MemberSignUpKafkaEvent event = signUpEvent();
        doThrow(new RuntimeException("DB 저장 실패")).when(notificationService).createNotification(any());

        assertThatThrownBy(() -> consumer.handle(event, ack))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 저장 실패");

        verify(processedEventService, never()).saveProcessedEvent(anyString(), anyString());
        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("정상 처리: 내역 저장 → 처리 기록 → 환영 메일 → 실시간 알림 → ack")
    void success() throws Exception {
        MemberSignUpKafkaEvent event = signUpEvent();

        consumer.handle(event, ack);

        var inOrder = inOrder(notificationService, processedEventService, emailService, simpMessagingTemplate, ack);
        inOrder.verify(notificationService).createNotification(any());
        inOrder.verify(processedEventService).saveProcessedEvent("member-group", event.getEventId());
        inOrder.verify(emailService).sendHtmlEmail(eq("user@example.com"), anyString(), anyString());
        inOrder.verify(simpMessagingTemplate).convertAndSend(eq("/topic/memberSignUp/7"), anyString());
        inOrder.verify(ack).acknowledge();
    }

    @Test
    @DisplayName("메일·ack는 커밋이 끝난 뒤(트랜잭션 밖)에서 보낸다 (메일 재시도·실패 기록이 커넥션을 붙잡거나 버려지지 않게)")
    void emailAndAck_happenAfterCommit() throws Exception {
        MemberSignUpKafkaEvent event = signUpEvent();

        consumer.handle(event, ack);

        var inOrder = inOrder(transactionManager, notificationService, processedEventService, emailService, ack);
        inOrder.verify(transactionManager).getTransaction(any());
        inOrder.verify(notificationService).createNotification(any());
        inOrder.verify(processedEventService).saveProcessedEvent("member-group", event.getEventId());
        inOrder.verify(transactionManager).commit(any());
        inOrder.verify(emailService).sendHtmlEmail(eq("user@example.com"), anyString(), anyString());
        inOrder.verify(ack).acknowledge();
    }

    @Test
    @DisplayName("메일 발송이 실패해도 내역은 저장됐으므로 ack한다")
    void emailFails_stillAcks() throws Exception {
        MemberSignUpKafkaEvent event = signUpEvent();
        doThrow(new RuntimeException("SMTP 오류")).when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());

        consumer.handle(event, ack);

        verify(processedEventService).saveProcessedEvent("member-group", event.getEventId());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("이미 처리된 이벤트면 저장 없이 ack")
    void duplicate_skips() throws Exception {
        MemberSignUpKafkaEvent event = signUpEvent();
        when(processedEventService.isAlreadyProcessed("member-group", event.getEventId())).thenReturn(true);

        consumer.handle(event, ack);

        verify(notificationService, never()).createNotification(any());
        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
        verify(ack).acknowledge();
    }

    private MemberSignUpKafkaEvent signUpEvent() {
        return MemberSignUpKafkaEvent.builder()
                .receiverId(7L)
                .username("tester")
                .email("user@example.com")
                .message("가입")
                .notificationType("SIGN_UP")
                .createdTime(LocalDateTime.now())
                .build();
    }
}
