package com.example.events.kafka;

import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.events.spring.ScheduleEvents;
import com.example.notification.model.NotificationModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvents {
    private Long receiverId;
    private String message;
    private ScheduleActionType notificationType;
    private NotificationChannel notificationChannel;
    private LocalDateTime createdTime;

    public static NotificationEvents of(ScheduleEvents events) {

        String message = switch (events.getNotificationType()) {
            case SCHEDULE_CREATED -> "📅 일정이 생성되었습니다: " + events.getContents();
            case SCHEDULE_UPDATE -> "✏️ 일정이 수정되었습니다: " + events.getContents();
            case SCHEDULE_DELETE -> "🗑️ 일정이 삭제되었습니다: " + events.getContents();
            case SCHEDULE_REMINDER -> "⏰ 일정 리마인드 알림: " + events.getContents();
        };

        return NotificationEvents
                .builder()
                .receiverId(events.getUserId())
                .message(message)
                .notificationType(events.getNotificationType())
                .notificationChannel(events.getNotificationChannel())
                .createdTime(events.getCreatedTime())
                .build();
    }

    public static NotificationEvents fromReminder(NotificationModel model) {
        return NotificationEvents.builder()
                .receiverId(model.getUserId())
                .message("⏰ 일정 리마인드 알림입니다: " + model.getMessage()) // 필요시 메시지 커스터마이징
                .notificationType(ScheduleActionType.SCHEDULE_REMINDER)
                .notificationChannel(NotificationChannel.WEB) // 또는 model에서 받아올 수 있음
                .createdTime(LocalDateTime.now())
                .build();
    }
}
