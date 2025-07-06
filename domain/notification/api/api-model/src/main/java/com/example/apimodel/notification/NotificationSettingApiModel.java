package com.example.apimodel.notification;


import lombok.Builder;

public class NotificationSettingApiModel {

    @Builder
    public record NotificationSettingRequest(
            boolean enabled,
            String userId
    ){}

    @Builder
    public record NotificationSettingResponse(
            Long id,
            String userId,
            boolean scheduleCreatedEnabled,
            boolean scheduleUpdatedEnabled,
            boolean scheduleDeletedEnabled,
            boolean scheduleRemindEnabled,
            boolean webEnabled,
            boolean emailEnabled,
            boolean pushEnabled
    ){ }
}
