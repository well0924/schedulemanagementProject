package com.example.controller.notification;

import com.example.apimodel.notification.NotificationSettingApiModel;
import com.example.inbound.notification.NotificationSettingInConnector;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notification-setting")
@AllArgsConstructor
public class NotificationSettingController {

    private final NotificationSettingInConnector notificationSettingInConnector;

    // 알림 설정 on/off
    @PutMapping("/me/all")
    public NotificationSettingApiModel.NotificationSettingResponse updateAllChannelToggle(@RequestBody NotificationSettingApiModel.NotificationSettingRequest request) {
        return notificationSettingInConnector.updateAllChannels(request.userId(), request.enabled());
    }

    // 알림설정 초기화
    @PostMapping("/me/reset")
    public void resetToDefault(@PathVariable("id")String userId) {
        notificationSettingInConnector.resetToDefault(userId);
    }

}
