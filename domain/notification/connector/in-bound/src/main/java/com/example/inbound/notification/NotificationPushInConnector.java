package com.example.inbound.notification;

import com.example.security.config.SecurityUtil;
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
        // 구독 대상 회원은 요청 body가 아니라 로그인한 회원으로 고정 (남의 번호로 구독 등록 방지)
        return notificationMapper.toApiModel(pushSubscriptionService
                .saveSubscription(SecurityUtil.currentUserId(),
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
