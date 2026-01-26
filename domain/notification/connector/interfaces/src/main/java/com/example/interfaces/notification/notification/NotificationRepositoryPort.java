package com.example.interfaces.notification.notification;

import com.example.notification.model.NotificationModel;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepositoryPort {

    List<NotificationModel> findPendingReminders(LocalDateTime now);

    List<NotificationModel> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<NotificationModel> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);

    NotificationModel saveNotification(NotificationModel notificationModel);

    NotificationModel findByMessageAndUserId(String message,Long userId);

    void markAsRead(Long id);

    void markAsSent(Long id);

    void markAsReminderSent(Long id);

    void deleteOldSentReminders(String type,LocalDateTime threshold);

    void deleteReminderByScheduleId(Long scheduleId);
}
