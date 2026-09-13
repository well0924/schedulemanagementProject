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
        log.info("Remind?");
        List<NotificationModel> dueReminders = notificationOutConnector.findPendingReminders(now);
        log.info("RemindAlarm::"+dueReminders);
        for (NotificationModel model : dueReminders) {
            try {
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
                    notificationOutConnector.markAsReminderSent(model.getId());
                    notificationOutConnector.markAsSent(model.getId());
                }
            } finally {
                MDC.clear();
            }
        }
    }

    // 생성 전용 - 방금 막 생성된 스케줄이라 기존 리마인더가 존재할 수 없으므로 DELETE 없이 INSERT만 한다 (2026-09-13)
    public void createReminder(SchedulesModel schedule) {
        notificationOutConnector.saveNotification(buildReminder(schedule));
    }

    // 수정 전용 - 시작시간이 바뀌면 리마인더 시각도 바뀌어야 하므로 기존 것을 지우고 새로 만든다
    public void upsertReminder(SchedulesModel schedule) {
        notificationOutConnector.deleteReminderByScheduleId(schedule.getId());
        notificationOutConnector.saveNotification(buildReminder(schedule));
    }

    private NotificationModel buildReminder(SchedulesModel schedule) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reminderTime = schedule.getStartTime().minusMinutes(5);

        // 만약 5분 전이 이미 지났다면?
        if (reminderTime.isBefore(now)) {
            log.info("⚠️ 리마인드 시간이 이미 지났습니다. 즉시 발송 대상으로 설정합니다.");
            reminderTime = now;
        }

        return NotificationModel.builder()
                .userId(schedule.getMemberId())
                .scheduleId(schedule.getId())
                .message("⏰ " + schedule.getContents() + " 일정이 곧 시작됩니다.")
                .notificationType(NotificationType.SCHEDULE_REMINDER)
                .isRead(false)
                .isSent(false)//일반 알림 여부
                .isReminderSent(false)// 리마인드 알림 여부
                .scheduledAt(reminderTime) // 5분 전 알림
                .build();
    }

    @Scheduled(cron = "0 0 4 * * ?") // 매일 새벽 4시
    public void deleteOldReminderNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(3);
        notificationOutConnector.deleteOldSentReminders("SCHEDULE_REMINDER", cutoff);
    }
}
