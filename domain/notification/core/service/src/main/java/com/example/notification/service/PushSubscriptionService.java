package com.example.notification.service;

import com.example.notification.model.PushSubscriptionModel;
import com.example.outbound.notification.PushSubscriptionOutConnector;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@AllArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionOutConnector pushSubscriptionOutConnector;

    // 푸시 구독
    public PushSubscriptionModel saveSubscription(Long userId, String endpoint, String p256dh, String auth, String userAgent) {
        return pushSubscriptionOutConnector.findByUserIdAndEndpoint(userId, endpoint)
                .map(existing -> {
                    existing = existing.toBuilder()
                            .active(true)
                            .revokedAt(null)
                            .build();
                    return pushSubscriptionOutConnector.savePush(existing);
                })
                .orElseGet(() -> {
                    PushSubscriptionModel subscription = PushSubscriptionModel.builder()
                            .memberId(userId)
                            .endpoint(endpoint)
                            .p256dh(p256dh)
                            .auth(auth)
                            .userAgent(userAgent)
                            .expirationTime(null)
                            .active(true)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return pushSubscriptionOutConnector.savePush(subscription);
                });
    }

    // 웹 푸시 구독 활성
    @Transactional(readOnly = true)
    public List<PushSubscriptionModel> getActiveSubscriptions(Long memberId) {
        return pushSubscriptionOutConnector.findByMemberIdAndActiveTrue(memberId);
    }

    // 웹 푸시 구독 해제
    public void deactivateAll(Long memberId) {
        pushSubscriptionOutConnector.deactivateAll(memberId);
    }

    public void deactivateByEndpoint(Long memberId, String endpoint) {
        pushSubscriptionOutConnector.deactivateByEndpoint(memberId, endpoint);
    }

}
