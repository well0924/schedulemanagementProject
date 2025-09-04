package com.example.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PushSubscriptionModel {
    private Long id;
    private Long memberId;
    private String endpoint;
    private String p256dh;
    private String auth;
    private LocalDateTime expirationTime; // millis nullable
    private String userAgent;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;
}
