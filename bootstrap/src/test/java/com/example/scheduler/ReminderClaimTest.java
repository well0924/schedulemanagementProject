package com.example.scheduler;

import com.example.events.outbox.OutboxEventService;
import com.example.notification.NotificationType;
import com.example.notification.model.NotificationModel;
import com.example.notification.service.ReminderNotificationService;
import com.example.outbound.notification.NotificationOutConnector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

// 앱 서버 여러 대가 같은 리마인드를 집어도 선점(claimReminder)에 성공한 쪽만 발송하는지 확인
class ReminderClaimTest {

    private final NotificationOutConnector connector = mock(NotificationOutConnector.class);
    private final OutboxEventService outboxEventService = mock(OutboxEventService.class);
    private final ReminderNotificationService service = new ReminderNotificationService(connector, outboxEventService);

    private NotificationModel dueReminder(long id) {
        return NotificationModel.builder()
                .id(id)
                .userId(63L)
                .scheduleId(87010L)
                .message("⏰ 테스트 일정이 곧 시작됩니다.")
                .notificationType(NotificationType.SCHEDULE_REMINDER)
                .isSent(false)
                .isReminderSent(false)
                .scheduledAt(LocalDateTime.now().minusMinutes(1))
                .build();
    }

    @Test
    @DisplayName("선점에 성공하면 Outbox에 리마인드 이벤트를 저장한다")
    void claimed_reminder_isPublished() {
        given(connector.findPendingReminders(any())).willReturn(List.of(dueReminder(1L)));
        given(connector.claimReminder(1L)).willReturn(true);

        service.sendReminderNotifications();

        verify(outboxEventService, times(1)).saveEvent(any(), anyString(), anyString(), anyString());
        verify(connector).markAsSent(1L);
    }

    @Test
    @DisplayName("다른 서버가 먼저 선점했으면 발송하지 않는다")
    void notClaimed_reminder_isSkipped() {
        given(connector.findPendingReminders(any())).willReturn(List.of(dueReminder(1L)));
        given(connector.claimReminder(1L)).willReturn(false);

        service.sendReminderNotifications();

        verifyNoInteractions(outboxEventService);
        verify(connector, never()).markAsSent(anyLong());
    }
}
