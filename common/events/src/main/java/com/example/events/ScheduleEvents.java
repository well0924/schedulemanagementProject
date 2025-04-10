package com.example.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleEvents {
    private Long scheduleId;
    private Long userId;
    private String actionType; // 알림종류 (생성,수정,삭제)
    private String contents;
    private LocalDateTime createdTime;
    private NotificationChannel notificationChannel;
}
