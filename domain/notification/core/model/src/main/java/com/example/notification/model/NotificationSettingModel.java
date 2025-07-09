package com.example.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingModel {

    private Long id;
    private String userId;
    private boolean scheduleCreatedEnabled;
    private boolean scheduleUpdatedEnabled;
    private boolean scheduleDeletedEnabled;
    private boolean scheduleRemindEnabled;
    private boolean webEnabled;
    private boolean emailEnabled;
    private boolean pushEnabled;
}
