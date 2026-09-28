package com.example.notification.service;

import com.example.exception.notification.dto.NotificationErrorCode;
import com.example.exception.notification.exception.NotificationCustomException;
import com.example.notification.model.NotificationModel;
import com.example.outbound.notification.NotificationOutConnector;
import com.example.security.config.SecurityUtil;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

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
                .isReminderSent(false)
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
        return notificationOutConnector.findPendingReminders(LocalDateTime.now());
    }

    public void markAsRead(Long id) {
        // 본인 알림만 읽음 처리 가능 (관리자 예외)
        Long ownerId = notificationOutConnector.findUserIdById(id);
        if (!SecurityUtil.hasRole("ADMIN") && !Objects.equals(ownerId, SecurityUtil.currentUserId())) {
            throw new NotificationCustomException(NotificationErrorCode.NOT_NOTIFICATION_OWNER);
        }
        notificationOutConnector.markAsRead(id); // 다시 저장 (업데이트)
    }

    public void markAsSent(Long id) {
        notificationOutConnector.markAsSent(id);
    }

    public void deleteReminderByScheduleId(Long scheduleId) {
        notificationOutConnector.deleteReminderByScheduleId(scheduleId);
    }

}
