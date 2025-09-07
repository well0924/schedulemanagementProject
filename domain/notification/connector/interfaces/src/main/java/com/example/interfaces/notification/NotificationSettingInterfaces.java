package com.example.interfaces.notification;

import com.example.apimodel.notification.NotificationSettingApiModel;
import com.example.events.enums.NotificationChannel;

public interface NotificationSettingInterfaces {

    NotificationSettingApiModel.NotificationSettingResponse updateAllChannels(Long userId, boolean enabled);

    void resetToDefault(Long userId);

    boolean isEnabled(Long memberId, NotificationChannel channel);
}
