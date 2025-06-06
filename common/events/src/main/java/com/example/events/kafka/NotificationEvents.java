package com.example.events.kafka;

import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.events.spring.ScheduleEvents;
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
}
