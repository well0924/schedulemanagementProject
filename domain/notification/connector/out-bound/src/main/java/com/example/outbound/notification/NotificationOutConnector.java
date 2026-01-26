package com.example.outbound.notification;

import com.example.exception.notification.dto.NotificationErrorCode;
import com.example.exception.notification.exception.NotificationCustomException;
import com.example.interfaces.notification.notification.NotificationRepositoryPort;
import com.example.notification.mapper.NotificationEntityMapper;
import com.example.notification.mapper.NotificationMapper;
import com.example.notification.model.NotificationModel;
import com.example.rdbrepository.Notification;
import com.example.rdbrepository.NotificationRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class NotificationOutConnector implements NotificationRepositoryPort {

    private final NotificationRepository notificationRepository;

    private final NotificationEntityMapper notificationEntityMapper;

    private final NotificationMapper notificationMapper;

    //리마인드 알림
    public List<NotificationModel> findPendingReminders(LocalDateTime now) {
        List<Notification> list = notificationRepository.findPendingReminders(now);
        return list
                .stream()
                .map(notificationMapper::toModel)
                .collect(Collectors.toList());
    }

    //특정 사용자(userId) 알림 조회 (최신순)
    public List<NotificationModel> findByUserIdOrderByCreatedAtDesc(Long userId) {
        List<Notification> list = notificationRepository.findByUserIdOrderByCreatedTimeDesc(userId);
        return list
                .stream()
                .map(notificationMapper::toModel)
                .collect(Collectors.toList());
    }

    //특정 사용자(userId)의 안 읽은 알림 조회
    public List<NotificationModel> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId) {
        List<Notification> list = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedTimeDesc(userId);
        return list
                .stream()
                .map(notificationMapper::toModel)
                .collect(Collectors.toList());
    }

    //알림 저장
    public NotificationModel saveNotification(NotificationModel notificationModel) {
        return notificationMapper
                .toModel(notificationRepository
                        .save(notificationEntityMapper.toEntity(notificationModel)));
    }

    public NotificationModel findByMessageAndUserId(String message,Long userId) {
        return notificationMapper
                .toModel(notificationRepository
                        .findByMessageAndUserId(message,userId));
    }

    public void markAsRead(Long id) {
        Notification notification = notificationRepository
                .findById(id)
                .orElseThrow(() -> new NotificationCustomException(NotificationErrorCode.NOTIFICATION_EMPTY));
        notification.markAsRead();
        notificationRepository.save(notification);
    }

    public void markAsSent(Long id) {
        notificationRepository.markAsSent(id);
    }

    public void markAsReminderSent(Long id) {
        notificationRepository.markAsReminderSent(id);
    }

    public void deleteOldSentReminders(String type,LocalDateTime threshold) {
        notificationRepository.deleteOldSentReminders(type,threshold);
    }

    public void deleteReminderByScheduleId(Long scheduleId) {
        notificationRepository.deleteReminderByScheduleId(scheduleId);
    }

}
