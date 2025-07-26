package com.example.interfaces.notification;

import com.example.apimodel.notification.NotificationSettingApiModel;

public interface NotificationSettingInterfaces {

    NotificationSettingApiModel.NotificationSettingResponse updateAllChannels(Long userId, boolean enabled);

    void resetToDefault(Long userId);
}
