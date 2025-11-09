package com.example.inbound.notification;

import com.example.apimodel.notification.NotificationSettingApiModel;
import com.example.events.enums.NotificationChannel;
import com.example.interfaces.notification.push.NotificationSettingInterfaces;
import com.example.notification.mapper.NotificationMapper;
import com.example.notification.service.NotificationSettingService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class NotificationSettingInConnector implements NotificationSettingInterfaces {

    private final NotificationSettingService notificationSettingService;

    private final NotificationMapper notificationMapper;

    @Override
    public NotificationSettingApiModel.NotificationSettingResponse updateAllChannels(Long userId, boolean enabled) {
        return notificationMapper.toModel(notificationSettingService.updateAllChannels(userId,enabled));
    }

    @Override
    public void resetToDefault(Long userId) {
        notificationSettingService.resetToDefault(userId);
    }

    @Override
    public boolean isEnabled(Long memberId, NotificationChannel channel) {
        return notificationSettingService.isEnabled(memberId,channel);
    }

}
