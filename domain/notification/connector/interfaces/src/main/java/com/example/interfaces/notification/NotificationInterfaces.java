package com.example.interfaces.notification;

import com.example.apimodel.notification.NotificationApiModel;
import com.example.model.schedules.SchedulesModel;

import java.util.List;

public interface NotificationInterfaces {

    List<NotificationApiModel.NotificationResponse> getNotificationsByUserId(Long userId);

    List<NotificationApiModel.NotificationResponse> getUnreadNotificationsByUserId(Long userId);

    List<NotificationApiModel.NotificationResponse> getScheduledNotificationsToSend();

    void markedRead(Long id);

    void createReminder(SchedulesModel schedule);
}
