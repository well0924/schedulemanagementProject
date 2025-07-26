package com.example.inbound.notification;

import com.example.apimodel.notification.NotificationSettingApiModel;
import com.example.interfaces.notification.NotificationSettingInterfaces;
import com.example.notification.model.NotificationSettingModel;
import com.example.notification.service.NotificationSettingService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class NotificationSettingInConnector implements NotificationSettingInterfaces {

    private final NotificationSettingService notificationSettingService;

    @Override
    public NotificationSettingApiModel.NotificationSettingResponse updateAllChannels(Long userId, boolean enabled) {
        return toModel(notificationSettingService.updateAllChannels(userId,enabled));
    }

    @Override
    public void resetToDefault(Long userId) {
        notificationSettingService.resetToDefault(userId);
    }

    private NotificationSettingApiModel.NotificationSettingResponse toModel(NotificationSettingModel model) {
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
}
