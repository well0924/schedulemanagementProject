package com.example.notification.service;

import com.example.events.enums.NotificationChannel;
import com.example.notification.model.NotificationSettingModel;
import com.example.outbound.notification.NotificationSettingOutConnector;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class NotificationSettingService {

    private final NotificationSettingOutConnector notificationSettingOutConnector;

    //알림 발송 여부 판단 (채널 기준만) -> 컨슈머 확인용.
    public boolean isEnabled(Long userId,NotificationChannel channel) {
        NotificationSettingModel notificationSettingModel = getSetting(userId);
        return isChannelEnabled(notificationSettingModel, channel);
    }

    //전체 알림 설정 저장
    public void updateSetting(NotificationSettingModel notificationSettingModel) {
        notificationSettingOutConnector.updateSetting(notificationSettingModel);
    }

    public NotificationSettingModel updateAllChannels(Long userId, boolean enabled) {
        NotificationSettingModel current = notificationSettingOutConnector.getOrCreate(userId);

        NotificationSettingModel updated = current
                .toBuilder()
                .webEnabled(enabled)
                .emailEnabled(enabled)
                .pushEnabled(enabled)
                .build();
        notificationSettingOutConnector.updateSetting(updated);
        return updated;
    }

    //초기값으로 되돌리기
    public void resetToDefault(Long userId) {
        NotificationSettingModel defaultSetting = NotificationSettingModel
                .builder()
                .userId(userId)
                .webEnabled(true)
                .emailEnabled(false)
                .pushEnabled(false)
                .build();
        notificationSettingOutConnector.updateSetting(defaultSetting);
    }

    public NotificationSettingModel getSetting(Long userId) {
        return notificationSettingOutConnector.getOrCreate(userId);
    }

    private boolean isChannelEnabled(NotificationSettingModel setting, NotificationChannel channel) {
        return switch (channel) {
            case WEB -> setting.isWebEnabled();
            case EMAIL -> setting.isEmailEnabled();
            case PUSH -> setting.isPushEnabled();
        };
    }
}
