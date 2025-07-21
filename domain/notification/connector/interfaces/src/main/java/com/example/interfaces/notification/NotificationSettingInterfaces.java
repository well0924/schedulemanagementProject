package com.example.interfaces.notification;

import com.example.apimodel.notification.NotificationSettingApiModel;

public interface NotificationSettingInterfaces {

    NotificationSettingApiModel.NotificationSettingResponse updateAllChannels(String userId, boolean enabled);

    void resetToDefault(String userId);
}
