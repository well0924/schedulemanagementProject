package com.example.outbound.notification;

import com.example.exception.notification.dto.NotificationErrorCode;
import com.example.exception.notification.exception.NotificationCustomException;
import com.example.notification.NotificationType;
import com.example.notification.model.NotificationModel;
import com.example.rdbrepository.Notification;
import com.example.rdbrepository.NotificationRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class NotificationOutConnector {

    private final NotificationRepository notificationRepository;

    //리마인드 알림
    public List<NotificationModel> findByScheduledAtBeforeAndIsSentFalse(LocalDateTime now) {
        List<Notification> list = notificationRepository.findByScheduledAtBeforeAndIsSentFalse(now);
        return list.stream().map(this::toModel).collect(Collectors.toList());
    }

    //특정 사용자(userId) 알림 조회 (최신순)
    public List<NotificationModel> findByUserIdOrderByCreatedAtDesc(Long userId) {
        List<Notification> list = notificationRepository.findByUserIdOrderByCreatedTimeDesc(userId);
        return list.stream().map(this::toModel).collect(Collectors.toList());
    }

    //특정 사용자(userId)의 안 읽은 알림 조회
    public List<NotificationModel> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId) {
        List<Notification> list = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedTimeDesc(userId);
        return list.stream().map(this::toModel).collect(Collectors.toList());
    }

    //알림 조회
    public NotificationModel findById(Long id){
        return toModel(notificationRepository
                .findById(id)
                .orElseThrow(()->new NotificationCustomException(NotificationErrorCode.INVALID_NOTIFICATION)));
    }

    //알림 저장
    public NotificationModel saveNotification(NotificationModel notificationModel) {

        Notification notification = Notification
                .builder()
                .id(notificationModel.getId())
                .isRead(false)
                .isSent(notificationModel.isSent())
                .message(notificationModel.getMessage())
                .notificationType(String.valueOf(notificationModel.getNotificationType()))
                .scheduledAt(notificationModel.getScheduledAt())
                .userId(notificationModel.getUserId())
                .scheduleId(notificationModel.getScheduleId())
                .build();
        return toModel(notificationRepository.save(notification));
    }

    public NotificationModel findByMessageAndUserId(String message,Long userId) {
        return toModel(notificationRepository.findByMessageAndUserId(message,userId));
    }

    public void markAsRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationCustomException(NotificationErrorCode.NOTIFICATION_EMPTY));
        notification.markAsRead();
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAsSent(Long id) {
        notificationRepository.markAsSent(id);
    }

    public void deleteOldSentReminders(String type,LocalDateTime threshold) {
        notificationRepository.deleteOldSentReminders(type,threshold);
    }

    public void deleteReminderByScheduleId(Long scheduleId) {
        notificationRepository.deleteReminderByScheduleId(scheduleId);
    }

    private NotificationModel toModel(Notification notification) {
        return NotificationModel
                .builder()
                .id(notification.getId())
                .message(notification.getMessage())
                .scheduleId(notification.getScheduleId())
                .userId(notification.getUserId())
                .isRead(notification.getIsRead())
                .isSent(notification.getIsSent())
                .notificationType(NotificationType.valueOf(notification.getNotificationType()))
                .scheduledAt(notification.getScheduledAt())
                .build();
    }
}
