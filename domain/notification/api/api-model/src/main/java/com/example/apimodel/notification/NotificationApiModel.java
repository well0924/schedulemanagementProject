package com.example.apimodel.notification;

import lombok.Builder;

import java.time.LocalDateTime;

public class NotificationApiModel {

    public record NotificationRequest(

    ) {}

    @Builder
    public record NotificationResponse(
            Long id,
            String message,
            Long userId,
            Long scheduleId,
            LocalDateTime scheduledAt) {}
}
