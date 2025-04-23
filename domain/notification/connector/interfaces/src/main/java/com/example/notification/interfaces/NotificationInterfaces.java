package com.example.notification.interfaces;

import com.example.notification.apimodel.NotificationApiModel;
import java.util.List;

public interface NotificationInterfaces {

    List<NotificationApiModel.NotificationResponse> getNotificationsByUserId(Long userId);

    List<NotificationApiModel.NotificationResponse> getUnreadNotificationsByUserId(Long userId);

    List<NotificationApiModel.NotificationResponse> getScheduledNotificationsToSend();

}
