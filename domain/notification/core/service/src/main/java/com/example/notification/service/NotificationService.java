package com.example.notification.service;

import com.example.notification.model.NotificationModel;
import com.example.outbound.notification.NotificationOutConnector;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@AllArgsConstructor
public class NotificationService {

    private final NotificationOutConnector notificationOutConnector;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationModel createNotification(NotificationModel model) {
        NotificationModel notificationModel = NotificationModel.builder()
                .userId(model.getUserId())
                .scheduleId(model.getScheduleId())
                .message(model.getMessage())
                .notificationType(model.getNotificationType())
                .isRead(false)
                .isSent(model.isSent())
                .scheduledAt(model.getScheduledAt())
                .build();

        return notificationOutConnector.saveNotification(notificationModel);
    }

    @Transactional(readOnly = true)
    public List<NotificationModel> getNotificationsByUserId(Long userId) {
        return notificationOutConnector.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public List<NotificationModel> getUnreadNotificationsByUserId(Long userId) {
        return notificationOutConnector.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<NotificationModel> getScheduledNotificationsToSend() {
        return notificationOutConnector.findByScheduledAtBeforeAndIsSentFalse(LocalDateTime.now());
    }

    public void markAsRead(Long id) {
        notificationOutConnector.markAsRead(id); // 다시 저장 (업데이트)
    }

    @Transactional
    public void markAsSent(Long id) {
        notificationOutConnector.markAsSent(id);
    }

    public NotificationModel findByMessageAndUserId(String message,Long userId) {
        return notificationOutConnector.findByMessageAndUserId(message, userId);
    }

}
