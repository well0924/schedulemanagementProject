package com.example.events;

import lombok.*;

import java.time.LocalDateTime;

@ToString
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleEvents {
    private Long scheduleId;
    private Long userId;
    private String contents;
    private ScheduleActionType notificationType; // 일정행위 (생성,수정,삭제)
    private NotificationChannel notificationChannel;
    private LocalDateTime createdTime;
}
