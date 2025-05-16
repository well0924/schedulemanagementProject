package com.example.apimodel.notification;

import lombok.Builder;

import java.time.LocalDateTime;

public class NotificationApiModel {

    public record NotificationRequest(

    ) {}

    @Builder
    public record NotificationResponse(
            long id,
            String message,
            long userId,
            long scheduleId,
            boolean isRead,
            boolean isSent,
            LocalDateTime scheduledAt) {}
}
