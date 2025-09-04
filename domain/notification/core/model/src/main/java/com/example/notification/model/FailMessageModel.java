package com.example.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailMessageModel {

    private Long id;

    private String topic;

    private String messageType;

    private String payload;

    private int retryCount;

    private boolean resolved;

    private boolean dead;

    private String exceptionMessage;

    private LocalDateTime lastTriedAt;

    private LocalDateTime resolvedAt;

    private LocalDateTime createdAt;

    public void resolveSuccess() {
        this.resolved = true;
        this.retryCount = this.retryCount; // 그대로
        this.lastTriedAt = LocalDateTime.now();
        this.resolvedAt = LocalDateTime.now();
    }

    public void resolveSuccess(String messageType) {
        this.resolved = true;
        this.messageType = messageType;
        this.lastTriedAt = LocalDateTime.now();
        this.resolvedAt = LocalDateTime.now();
    }

    public void resolveFailure(Exception ex) {
        this.retryCount++;
        this.lastTriedAt = LocalDateTime.now();
        this.exceptionMessage = ex.getMessage();
    }

    public void markAsDead() {
        this.resolved = false;
        this.dead = true;
        this.lastTriedAt = LocalDateTime.now();
    }
}
