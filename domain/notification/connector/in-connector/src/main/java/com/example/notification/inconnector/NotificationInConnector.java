package com.example.notification.inconnector;

import com.example.notification.apimodel.NotificationApiModel;
import com.example.notification.interfaces.NotificationInterfaces;
import com.example.notification.model.NotificationModel;
import com.example.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class NotificationInConnector implements NotificationInterfaces{

    private final NotificationService notificationService;

    @Override
    public List<NotificationApiModel.NotificationResponse> getNotificationsByUserId(Long userId) {
        List<NotificationModel> result = notificationService.getNotificationsByUserId(userId);
        return result.stream().map(this::toApiModelResponse).collect(Collectors.toList());
    }

    @Override
    public List<NotificationApiModel.NotificationResponse> getUnreadNotificationsByUserId(Long userId) {
        return notificationService.getUnreadNotificationsByUserId(userId).stream().map(this::toApiModelResponse).collect(Collectors.toList());
    }

    @Override
    public List<NotificationApiModel.NotificationResponse> getScheduledNotificationsToSend() {
        return notificationService.getScheduledNotificationsToSend()
                .stream()
                .map(this::toApiModelResponse)
                .collect(Collectors.toList());
    }

    private NotificationApiModel.NotificationResponse toApiModelResponse(NotificationModel notificationModel) {
        return NotificationApiModel.NotificationResponse
                .builder()
                .id(notificationModel.getId())
                .message(notificationModel.getMessage())
                .scheduleId(notificationModel.getScheduleId())
                .userId(notificationModel.getUserId())
                .scheduledAt(notificationModel.getScheduledAt())
                .build();
    }
}
