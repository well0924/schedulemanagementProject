package com.example.notification.mapper;

import com.example.notification.model.FailMessageModel;
import com.example.notification.model.NotificationModel;
import com.example.notification.model.NotificationSettingModel;
import com.example.notification.model.PushSubscriptionModel;
import com.example.rdbrepository.FailedMessage;
import com.example.rdbrepository.Notification;
import com.example.rdbrepository.NotificationSetting;
import com.example.rdbrepository.PushSubscription;
import org.springframework.stereotype.Component;

@Component
public class NotificationEntityMapper {

    public Notification toEntity(NotificationModel notificationModel) {
        return Notification
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
    }

    public NotificationSetting toEntity(NotificationSettingModel model) {
        return NotificationSetting
                .builder()
                .id(model.getId())
                .userId(model.getUserId())
                .webEnabled(model.isWebEnabled())
                .emailEnabled(model.isEmailEnabled())
                .pushEnabled(model.isPushEnabled())
                .build();
    }

    public PushSubscription toEntity(PushSubscriptionModel pushSubscriptionModel) {
        return PushSubscription
                .builder()
                .active(pushSubscriptionModel.isActive())
                .auth(pushSubscriptionModel.getAuth())
                .endpoint(pushSubscriptionModel.getEndpoint())
                .p256dh(pushSubscriptionModel.getP256dh())
                .userAgent(pushSubscriptionModel.getUserAgent())
                .memberId(pushSubscriptionModel.getMemberId())
                .expirationTime(pushSubscriptionModel.getExpirationTime())
                .createdAt(pushSubscriptionModel.getCreatedAt())
                .revokedAt(pushSubscriptionModel.getRevokedAt())
                .build();
    }

    public FailedMessage toEntity(FailMessageModel failMessageModel) {
        return FailedMessage
                .builder()
                .id(failMessageModel.getId())
                .messageType(failMessageModel.getMessageType())
                .exceptionMessage(failMessageModel.getExceptionMessage())
                .topic(failMessageModel.getTopic())
                .payload(failMessageModel.getPayload())
                .retryCount(failMessageModel.getRetryCount())
                .resolved(failMessageModel.isResolved())
                .createdAt(failMessageModel.getCreatedAt())
                .resolvedAt(failMessageModel.getResolvedAt())
                .lastTriedAt(failMessageModel.getLastTriedAt())
                .build();
    }
}
