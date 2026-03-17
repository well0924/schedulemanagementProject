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

    private String eventId;

    private String topic;

    private String messageType;

    private String payload;

    private int retryCount;

    private boolean resolved;

    private boolean dead;

    private String exceptionMessage;
    // 언제 메시지를 읽을지 결정하는 필드
    private LocalDateTime nextRetryTime;

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
        this.nextRetryTime = null; // 성공을 했으니 다음시간은 지우기.
    }

    public void resolveFailure(Exception ex) {
        this.retryCount++;
        this.lastTriedAt = LocalDateTime.now();
        this.exceptionMessage = ex.getMessage();
        // retry Topic 으로 보내기 위해서 다음 실행 시간을 지수적으로 증가
        this.nextRetryTime = switch (this.retryCount) {
            case 1 -> LocalDateTime.now().plusSeconds(30);
            case 2 -> LocalDateTime.now().plusMinutes(1);
            case 3 -> LocalDateTime.now().plusMinutes(5);
            case 4 -> LocalDateTime.now().plusMinutes(10);
            default -> LocalDateTime.now().plusMinutes(30);
        };
    }

    public void markAsDead() {
        this.resolved = false;
        this.dead = true;
        this.lastTriedAt = LocalDateTime.now();
        this.nextRetryTime = null;
    }
}
