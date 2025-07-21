package com.example.notification.service;

import com.example.events.enums.AggregateType;
import com.example.events.enums.ScheduleActionType;
import com.example.events.kafka.NotificationEvents;
import com.example.events.outbox.OutboxEventService;
import com.example.model.schedules.SchedulesModel;
import com.example.notification.NotificationType;
import com.example.notification.model.NotificationModel;
import com.example.outbound.notification.NotificationOutConnector;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class ReminderNotificationService {

    private final NotificationOutConnector notificationOutConnector;
    private final OutboxEventService outboxEventService;


    @Scheduled(cron = "0 * * * * *")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendReminderNotifications() {
        LocalDateTime now = LocalDateTime.now();
        List<NotificationModel> dueReminders = notificationOutConnector.
                findByScheduledAtBeforeAndIsSentFalse(now);

        for (NotificationModel model : dueReminders) {
            if (model.isReadyToSend(now)) {
                MDC.put("receiverId", String.valueOf(model.getUserId()));
                MDC.put("scheduleId", String.valueOf(model.getScheduleId()));
                MDC.put("notificationType", String.valueOf(ScheduleActionType.SCHEDULE_REMINDER));
                log.info("🔔 알림 전송 대상 확인: userId={}, message={}", model.getUserId(), model.getMessage());

                NotificationEvents event = NotificationEvents.fromReminder(model); // 아래에 정의할 팩토리 메서드

                // outbox로 전송
                outboxEventService.saveEvent(
                        event,
                        AggregateType.SCHEDULE.name(),
                        model.getId().toString(),
                        event.getNotificationType().name()
                );
                notificationOutConnector.markAsSent(model.getId());
            }
        }
    }

    public void createReminder(SchedulesModel schedule) {
        // 1. 기존 알림 삭제
        notificationOutConnector.deleteReminderByScheduleId(schedule.getId());

        // 2. 새 알림 등록
        NotificationModel reminder = NotificationModel.builder()
                .userId(schedule.getUserId())
                .scheduleId(schedule.getId())
                .message("⏰ " + schedule.getContents() + " 일정이 곧 시작됩니다.")
                .notificationType(NotificationType.SCHEDULE_REMINDER)
                .isRead(false)
                .isSent(false)
                .scheduledAt(schedule.getStartTime().minusMinutes(5)) // 5분 전 알림
                .build();

        notificationOutConnector.saveNotification(reminder);
    }

    @Scheduled(cron = "0 0 4 * * ?") // 매일 새벽 4시
    public void deleteOldReminderNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(3);
        notificationOutConnector.deleteOldSentReminders("SCHEDULE_REMINDER", cutoff);
    }
}
