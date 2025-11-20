package com.example.service.schedule.support;

import com.example.events.enums.NotificationChannel;
import com.example.interfaces.notification.push.NotificationSettingInterfaces;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final NotificationSettingInterfaces notificationSettingInConnector;

    public NotificationChannel resolveChannel(Long userId) {
        boolean webEnabled = notificationSettingInConnector.isEnabled(userId, NotificationChannel.WEB);
        log.info("webAlarm::"+webEnabled);
        boolean pushEnabled = notificationSettingInConnector.isEnabled(userId, NotificationChannel.PUSH);
        log.info("pushAlarm::"+pushEnabled);
        if (pushEnabled) {
            return NotificationChannel.PUSH; // PUSH 우선
        } else if (webEnabled) {
            return NotificationChannel.WEB;
        } else {
            return NotificationChannel.WEB; // 기본값
        }
    }

}
