package com.example.interfaces.notification.push;

import com.example.notification.model.NotificationSettingModel;

public interface NotificationSettingRepositoryPort {

    NotificationSettingModel getOrCreate(Long userId);

    void updateSetting(NotificationSettingModel notificationSettingModel);

}
