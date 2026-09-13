package com.example.interfaces.notification.notification;

import com.example.apimodel.notification.NotificationApiModel;
import com.example.model.schedules.SchedulesModel;

import java.util.List;

public interface NotificationInterfaces {

    List<NotificationApiModel.NotificationResponse> getNotificationsByUserId(Long userId);

    List<NotificationApiModel.NotificationResponse> getUnreadNotificationsByUserId(Long userId);

    List<NotificationApiModel.NotificationResponse> getScheduledNotificationsToSend();

    void markedRead(Long id);

    // 생성 전용 - 기존 리마인더가 있을 수 없으므로 DELETE 없이 INSERT만 수행
    void createReminder(SchedulesModel schedule);

    // 수정 전용 - 기존 리마인더를 지우고 새로 만든다 (DELETE+INSERT)
    void upsertReminder(SchedulesModel schedule);

    void deleteReminderByScheduleId(Long scheduleId);
}
