package com.example.inbound.notification;

import com.example.apimodel.notification.NotificationPushApiModel;
import com.example.interfaces.notification.push.NotificationPushInterfaces;
import com.example.notification.mapper.NotificationMapper;
import com.example.notification.service.PushSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class NotificationPushInConnector implements NotificationPushInterfaces {

    private final PushSubscriptionService pushSubscriptionService;

    private final NotificationMapper notificationMapper;

    @Override
    public NotificationPushApiModel.NotificationPushResponse subscribe(NotificationPushApiModel.NotificationPushRequest request) {
        return notificationMapper.toApiModel(pushSubscriptionService
                .saveSubscription(request.memberId(),
                        request.endpoint(),
                        request.p256dh(),
                        request.auth(),
                        request.userAgent()));
    }

    @Override
    public List<NotificationPushApiModel.NotificationPushResponse> getActiveSubscriptions(Long memberId) {
        return pushSubscriptionService.getActiveSubscriptions(memberId)
                .stream()
                .map(notificationMapper::toApiModel)
                .collect(Collectors.toList());
    }

    @Override
    public void deactivateAll(Long memberId) {
        pushSubscriptionService.deactivateAll(memberId);
    }

    @Override
    public void deactivateByEndpoint(Long memberId, String endpoint) {
        pushSubscriptionService.deactivateByEndpoint(memberId, endpoint);
    }

}
