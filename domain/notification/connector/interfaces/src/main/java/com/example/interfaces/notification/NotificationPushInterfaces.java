package com.example.interfaces.notification;

import com.example.apimodel.notification.NotificationPushApiModel;

import java.util.List;

public interface NotificationPushInterfaces {

    NotificationPushApiModel.NotificationPushResponse subscribe(NotificationPushApiModel.NotificationPushRequest request);

    List<NotificationPushApiModel.NotificationPushResponse> getActiveSubscriptions(Long memberId);

    void deactivateAll(Long memberId);

    void deactivateByEndpoint(Long memberId, String endpoint);

}
