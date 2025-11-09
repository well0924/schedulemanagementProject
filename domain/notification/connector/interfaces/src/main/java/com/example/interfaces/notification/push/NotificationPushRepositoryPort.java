package com.example.interfaces.notification.push;

import com.example.notification.model.PushSubscriptionModel;

import java.util.List;
import java.util.Optional;

public interface NotificationPushRepositoryPort {

    List<PushSubscriptionModel> findByMemberIdAndActiveTrue(Long memberId);

    PushSubscriptionModel savePush(PushSubscriptionModel pushSubscriptionModel);

    Optional<PushSubscriptionModel> findByUserIdAndEndpoint(Long memberId, String endpoint);

    void deactivateAll(Long memberId);

    void deactivateByEndpoint(Long memberId, String endpoint);


}
