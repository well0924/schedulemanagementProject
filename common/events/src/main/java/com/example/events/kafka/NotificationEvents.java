package com.example.events.kafka;

import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
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
}
