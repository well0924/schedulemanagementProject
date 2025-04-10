package com.example.notification.model;

import com.example.notification.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationModel {

    private Long id;
    private String message;
    private NotificationType notificationType;
    private boolean isRead;
    private boolean isSent;
    private Long userId;
    private Long scheduleId;
    private LocalDateTime scheduledAt;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    // 알림 읽음 처리
    public void markAsRead() {
        this.isRead = true;
    }

    // 알림 발송 완료 처리
    public void markAsSent() {
        this.isSent = true;
    }

    // 지금 발송해도 되는지 체크 (리마인드용)
    public boolean isReadyToSend(LocalDateTime now) {
        return scheduledAt != null && !isSent && now.isAfter(scheduledAt);
    }
}
