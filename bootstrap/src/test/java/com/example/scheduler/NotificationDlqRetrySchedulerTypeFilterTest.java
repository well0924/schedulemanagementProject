package com.example.scheduler;

import com.example.events.enums.ScheduleActionType;
import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import com.example.inbound.consumer.schedule.NotificationDlqRetryScheduler;
import com.example.notification.model.FailMessageModel;
import com.example.notification.service.FailedMessageService;
import com.example.notification.service.WebPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

// 알림 DLQ 스케줄러가 회원가입(MEMBER_SIGNUP) 실패 메시지를 건드리지 않는지 확인 (브로커 없이 단위 테스트)
class NotificationDlqRetrySchedulerTypeFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final FailedMessageService failedMessageService = mock(FailedMessageService.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, NotificationEvents> kafkaTemplate = mock(KafkaTemplate.class);
    private final WebPushService webPushService = mock(WebPushService.class);

    private final NotificationDlqRetryScheduler scheduler =
            new NotificationDlqRetryScheduler(failedMessageService, kafkaTemplate, webPushService, objectMapper);

    @BeforeEach
    void setUp() {
        // 스케줄러가 브로커 응답(future)을 기다리므로 기본은 전송 성공으로 둔다
        given(kafkaTemplate.send(anyString(), any(), any(NotificationEvents.class)))
                .willReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void 회원가입_실패메시지는_알림_스케줄러가_처리하지_않는다() throws Exception {
        FailMessageModel signup = failMessage(1L, "MEMBER_SIGNUP",
                objectMapper.writeValueAsString(MemberSignUpKafkaEvent.builder()
                        .receiverId(1L).notificationType("SIGN_UP").message("가입").build()));
        given(failedMessageService.findReadyToRetry()).willReturn(List.of(signup));

        scheduler.retryNotifications();

        verify(failedMessageService, never()).updateFailMessage(any());
        verifyNoInteractions(kafkaTemplate);
        assertThat(signup.getRetryCount()).isZero();
        assertThat(signup.getExceptionMessage()).isNull();
    }

    @Test
    void 알림_실패메시지는_기존대로_재시도_토픽으로_보낸다() throws Exception {
        FailMessageModel signup = failMessage(1L, "MEMBER_SIGNUP", "{}");
        FailMessageModel notification = failMessage(2L, "NOTIFICATION",
                objectMapper.writeValueAsString(NotificationEvents.builder()
                        .receiverId(1L).scheduleId(10L).message("알림")
                        .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                        .build()));
        given(failedMessageService.findReadyToRetry()).willReturn(List.of(signup, notification));

        scheduler.retryNotifications();

        verify(kafkaTemplate).send(eq("notification-events.retry.5s"), any(), any(NotificationEvents.class));
        verify(failedMessageService, times(1)).updateFailMessage(notification);
        verify(failedMessageService, never()).updateFailMessage(signup);
        assertThat(notification.isResolved()).isTrue();
        // 알림 유형(SCHEDULE_CREATED)으로 덮어쓰지 않아야 다시 열렸을 때 이 스케줄러가 다시 집는다
        assertThat(notification.getMessageType()).isEqualTo("NOTIFICATION");
    }

    @Test
    void 재시도_토픽_전송이_실패하면_성공으로_기록하지_않는다() throws Exception {
        FailMessageModel notification = failMessage(2L, "NOTIFICATION", notificationJson());
        given(failedMessageService.findReadyToRetry()).willReturn(List.of(notification));
        given(kafkaTemplate.send(anyString(), any(), any(NotificationEvents.class)))
                .willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        scheduler.retryNotifications();

        assertThat(notification.isResolved()).isFalse();
        assertThat(notification.getRetryCount()).isEqualTo(1);
        assertThat(notification.getNextRetryTime()).isAfter(LocalDateTime.now());
        verify(failedMessageService).updateFailMessage(notification);
    }

    @Test
    void 재시도_횟수가_다_차면_final_토픽으로_보내고_dead로_둔다() throws Exception {
        FailMessageModel notification = failMessage(2L, "NOTIFICATION", notificationJson(), 4);
        given(failedMessageService.findReadyToRetry()).willReturn(List.of(notification));

        scheduler.retryNotifications();

        verify(kafkaTemplate).send(eq("notification-events.retry.final"), any(), any(NotificationEvents.class));
        assertThat(notification.isDead()).isTrue();
        assertThat(notification.isResolved()).isFalse();
    }

    private String notificationJson() throws Exception {
        return objectMapper.writeValueAsString(NotificationEvents.builder()
                .receiverId(1L).scheduleId(10L).message("알림")
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .build());
    }

    private FailMessageModel failMessage(Long id, String type, String payload) {
        return failMessage(id, type, payload, 0);
    }

    private FailMessageModel failMessage(Long id, String type, String payload, int retryCount) {
        return FailMessageModel.builder()
                .id(id)
                .messageType(type)
                .payload(payload)
                .retryCount(retryCount)
                .nextRetryTime(LocalDateTime.now().minusSeconds(1))
                .build();
    }
}
