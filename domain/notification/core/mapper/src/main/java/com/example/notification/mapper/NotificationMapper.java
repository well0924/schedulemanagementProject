package com.example.notification.mapper;

import com.example.apimodel.notification.NotificationApiModel;
import com.example.apimodel.notification.NotificationPushApiModel;
import com.example.apimodel.notification.NotificationSettingApiModel;
import com.example.notification.NotificationType;
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
public class NotificationMapper {

    public NotificationModel toModel(Notification notification) {
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

    public NotificationSettingModel toModel(NotificationSetting entity) {
        return NotificationSettingModel
                .builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .webEnabled(entity.isWebEnabled())
                .emailEnabled(entity.isEmailEnabled())
                .pushEnabled(entity.isPushEnabled())
                .build();
    }

    public PushSubscriptionModel toModel(PushSubscription pushSubscription) {
        return PushSubscriptionModel
                .builder()
                .memberId(pushSubscription.getMemberId())
                .auth(pushSubscription.getAuth())
                .active(pushSubscription.isActive())
                .p256dh(pushSubscription.getP256dh())
                .endpoint(pushSubscription.getEndpoint())
                .userAgent(pushSubscription.getUserAgent())
                .createdAt(pushSubscription.getCreatedAt())
                .expirationTime(pushSubscription.getExpirationTime())
                .revokedAt(pushSubscription.getRevokedAt())
                .build();
    }

    public FailMessageModel toModel(FailedMessage failedMessage) {
        return FailMessageModel
                .builder()
                .id(failedMessage.getId())
                .exceptionMessage(failedMessage.getExceptionMessage())
                .topic(failedMessage.getTopic())
                .payload(failedMessage.getPayload())
                .resolved(failedMessage.isResolved())
                .retryCount(failedMessage.getRetryCount())
                .createdAt(failedMessage.getCreatedAt())
                .resolvedAt(failedMessage.getResolvedAt())
                .lastTriedAt(failedMessage.getLastTriedAt())
                .build();
    }

    public NotificationApiModel.NotificationResponse toApiModelResponse(NotificationModel notificationModel) {
        return NotificationApiModel.NotificationResponse
                .builder()
                .id(notificationModel.getId())
                .message(notificationModel.getMessage())
                .scheduleId(notificationModel.getScheduleId())
                .scheduledAt(notificationModel.getScheduledAt())
                .userId(notificationModel.getUserId())
                .isRead(notificationModel.isRead())
                .isSent(notificationModel.isSent())
                .build();
    }

    public NotificationSettingApiModel.NotificationSettingResponse toModel(NotificationSettingModel model) {
        return NotificationSettingApiModel
                .NotificationSettingResponse
                .builder()
                .id(model.getId())
                .userId(model.getUserId())
                .webEnabled(model.isWebEnabled())
                .emailEnabled(model.isEmailEnabled())
                .pushEnabled(model.isPushEnabled())
                .scheduleCreatedEnabled(model.isScheduleCreatedEnabled())
                .scheduleUpdatedEnabled(model.isScheduleUpdatedEnabled())
                .scheduleDeletedEnabled(model.isScheduleDeletedEnabled())
                .scheduleRemindEnabled(model.isScheduleRemindEnabled())
                .build();
    }

    public NotificationPushApiModel.NotificationPushResponse toApiModel(PushSubscriptionModel pushSubscriptionModel) {
        return NotificationPushApiModel.NotificationPushResponse
                .builder()
                .id(pushSubscriptionModel.getId())
                .auth(pushSubscriptionModel.getAuth())
                .active(pushSubscriptionModel.isActive())
                .p256dh(pushSubscriptionModel.getP256dh())
                .endpoint(pushSubscriptionModel.getEndpoint())
                .userAgent(pushSubscriptionModel.getUserAgent())
                .memberId(pushSubscriptionModel.getMemberId())
                .createdAt(pushSubscriptionModel.getCreatedAt())
                .expirationTime(pushSubscriptionModel.getExpirationTime())
                .revokedAt(pushSubscriptionModel.getRevokedAt())
                .build();
    }
}
