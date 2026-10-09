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
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    }

    private FailMessageModel failMessage(Long id, String type, String payload) {
        return FailMessageModel.builder()
                .id(id)
                .messageType(type)
                .payload(payload)
                .retryCount(0)
                .nextRetryTime(LocalDateTime.now().minusSeconds(1))
                .build();
    }
}
