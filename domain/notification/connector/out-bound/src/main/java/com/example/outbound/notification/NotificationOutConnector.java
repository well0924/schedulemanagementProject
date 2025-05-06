package com.example.outbound.notification;

import com.example.notification.NotificationType;
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
public class NotificationOutConnector {

    private final NotificationRepository notificationRepository;

    //리마인드 알림
    public List<NotificationModel> findByScheduledAtBeforeAndIsSentFalse(LocalDateTime now) {
        List<Notification> list = notificationRepository.findByScheduledAtBeforeAndIsSentFalse(now);
        if(list.isEmpty()) {
            throw new RuntimeException("알림 목록이 없습니다.");
        }
        return list.stream().map(this::toModel).collect(Collectors.toList());
    }

    //특정 사용자(userId) 알림 조회 (최신순)
    public List<NotificationModel> findByUserIdOrderByCreatedAtDesc(Long userId) {
        List<Notification> list = notificationRepository.findByUserIdOrderByCreatedTimeDesc(userId);
        if(list.isEmpty()) {
            throw new RuntimeException("알림 목록이 없습니다.");
        }
        return list.stream().map(this::toModel).collect(Collectors.toList());
    }

    //특정 사용자(userId)의 안 읽은 알림 조회
    public List<NotificationModel> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId) {
        List<Notification> list = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedTimeDesc(userId);
        if(list.isEmpty()) {
            throw new RuntimeException("알림 목록이 없습니다.");
        }
        return list.stream().map(this::toModel).collect(Collectors.toList());
    }

    //알림 저장
    public NotificationModel saveNotification(NotificationModel notificationModel) {

        Notification notification = Notification
                .builder()
                .isRead(false)
                .isSent(false)
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

    private NotificationModel toModel(Notification notification) {
        return NotificationModel
                .builder()
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
