package com.example.inbound.notification;

import com.example.apimodel.notification.NotificationPushApiModel;
import com.example.interfaces.notification.NotificationPushInterfaces;
import com.example.notification.model.PushSubscriptionModel;
import com.example.notification.service.PushSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class NotificationPushInConnector implements NotificationPushInterfaces {

    private final PushSubscriptionService pushSubscriptionService;

    @Override
    public NotificationPushApiModel.NotificationPushResponse subscribe(NotificationPushApiModel.NotificationPushRequest request) {
        return toApiModel(pushSubscriptionService
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
                .map(this::toApiModel)
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

    private NotificationPushApiModel.NotificationPushResponse toApiModel(PushSubscriptionModel pushSubscriptionModel) {
        return NotificationPushApiModel.NotificationPushResponse
                .builder()
                .id(pushSubscriptionModel.getId())
                .auth(pushSubscriptionModel.getAuth())
                .active(pushSubscriptionModel.isActive())
                .p256dh(pushSubscriptionModel.getP256dh())
                .endpoint(pushSubscriptionModel.getEndpoint())
                .userAgent(pushSubscriptionModel.getUserAgent())
                .memberId(pushSubscriptionModel.getMemberId())
                .createdAt(pushSubscriptionModel.getCreatedAt())
                .expirationTime(pushSubscriptionModel.getExpirationTime())
                .revokedAt(pushSubscriptionModel.getRevokedAt())
                .build();
    }

}
