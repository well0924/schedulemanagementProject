package com.example.apimodel.notification;

import lombok.Builder;
import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;

public class NotificationPushApiModel {

    @Builder
    public record NotificationPushRequest(
            Long memberId,
            String endpoint,
            String p256dh,
            String auth,
            String userAgent
    ){}

    @Builder
    public record NotificationPushResponse(
            Long id,
            Long memberId,
            String endpoint,
            String p256dh,
            String auth,
            LocalDateTime expirationTime, // millis nullable
            String userAgent,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime revokedAt) {

    }

}
