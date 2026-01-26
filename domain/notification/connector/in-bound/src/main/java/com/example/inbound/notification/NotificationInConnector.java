package com.example.inbound.notification;


import com.example.apimodel.notification.NotificationApiModel;
import com.example.interfaces.notification.notification.NotificationInterfaces;
import com.example.notification.mapper.NotificationMapper;
import com.example.notification.model.NotificationModel;
import com.example.notification.service.NotificationService;
import com.example.notification.service.ReminderNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class NotificationInConnector implements NotificationInterfaces{

    private final NotificationService notificationService;

    private final ReminderNotificationService reminderNotificationService;

    private final NotificationMapper notificationMapper;

    @Override
    public List<NotificationApiModel.NotificationResponse> getNotificationsByUserId(Long userId) {
        List<NotificationModel> result = notificationService.getNotificationsByUserId(userId);
        return result.stream().map(notificationMapper::toApiModelResponse).collect(Collectors.toList());
    }

    @Override
    public List<NotificationApiModel.NotificationResponse> getUnreadNotificationsByUserId(Long userId) {
        return notificationService.getUnreadNotificationsByUserId(userId).stream().map(notificationMapper::toApiModelResponse).collect(Collectors.toList());
    }

    @Override
    public List<NotificationApiModel.NotificationResponse> getScheduledNotificationsToSend() {
        return notificationService.getScheduledNotificationsToSend()
                .stream()
                .map(notificationMapper::toApiModelResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void markedRead(Long id) {
        notificationService.markAsRead(id);
    }

    @Override
    public void createReminder(com.example.model.schedules.SchedulesModel schedule) {
        reminderNotificationService.createReminder(schedule);
    }

    @Override
    public void deleteReminderByScheduleId(Long scheduleId) {
        notificationService.deleteReminderByScheduleId(scheduleId);
    }
}
