package com.example.events.kafka;

import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.events.spring.ScheduleEvents;
import com.example.notification.model.NotificationModel;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@SuperBuilder
public class NotificationEvents extends BaseKafkaEvent{
    private Long receiverId; // memberId 회원 번호
    private Long scheduleId; //일정 번호
    private String message;
    private ScheduleActionType notificationType;// 일정 유형
    private NotificationChannel notificationChannel;
    private boolean forceSend; // dlq 적용시 강제 적용.
    private LocalDateTime scheduleAt;
    private LocalDateTime createdTime;

    public void setForceSend(boolean forceSend) {
        this.forceSend = forceSend;
    }

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
                .scheduleId(events.getScheduleId())
                .message(message)
                .notificationType(events.getNotificationType())
                .notificationChannel(events.getNotificationChannel())
                .createdTime(events.getCreatedTime())
                .scheduleAt(events.getStartTime().minusMinutes(5))
                .build();
    }

    public static NotificationEvents fromReminder(NotificationModel model) {
        return NotificationEvents.builder()
                .receiverId(model.getUserId())
                .message("⏰ 일정 리마인드 알림입니다: " + model.getMessage()) // 필요시 메시지 커스터마이징
                .notificationType(ScheduleActionType.SCHEDULE_REMINDER)
                .notificationChannel(NotificationChannel.WEB) // 또는 model에서 받아올 수 있음
                .createdTime(LocalDateTime.now())
                .scheduleId(model.getScheduleId())
                .build();
    }
}
