package com.example.outbound.notification;

import com.example.notification.model.PushSubscriptionModel;
import com.example.rdbrepository.PushSubscription;
import com.example.rdbrepository.PushSubscriptionRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class PushSubscriptionOutConnector {

    private final PushSubscriptionRepository pushSubscriptionRepository;

    public List<PushSubscriptionModel> findByMemberIdAndActiveTrue(Long memberId) {
        return pushSubscriptionRepository
                .findByMemberIdAndActiveTrue(memberId)
                .stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    public PushSubscriptionModel savePush(PushSubscriptionModel pushSubscriptionModel) {
        PushSubscription pushSubscription = PushSubscription
                .builder()
                .active(pushSubscriptionModel.isActive())
                .auth(pushSubscriptionModel.getAuth())
                .endpoint(pushSubscriptionModel.getEndpoint())
                .p256dh(pushSubscriptionModel.getP256dh())
                .userAgent(pushSubscriptionModel.getUserAgent())
                .memberId(pushSubscriptionModel.getMemberId())
                .expirationTime(pushSubscriptionModel.getExpirationTime())
                .createdAt(pushSubscriptionModel.getCreatedAt())
                .revokedAt(pushSubscriptionModel.getRevokedAt())
                .build();
        return toModel(pushSubscriptionRepository.save(pushSubscription));
    }

    public Optional<PushSubscriptionModel> findByUserIdAndEndpoint(Long memberId, String endpoint) {
        return pushSubscriptionRepository
                .findByMemberIdAndActiveTrue(memberId)
                .stream()
                .filter(sub -> sub.getEndpoint().equals(endpoint))
                .map(this::toModel)
                .findFirst();
    }

    public void deactivateAll(Long memberId){
        pushSubscriptionRepository.findByMemberIdAndActiveTrue(memberId)
                .forEach(PushSubscription::deactivate);
    }

    public void deactivateByEndpoint(Long memberId, String endpoint) {
        pushSubscriptionRepository.findByMemberIdAndActiveTrue(memberId)
                .stream()
                .filter(s -> s.getEndpoint().equals(endpoint))
                .forEach(PushSubscription::deactivate);
    }

    private PushSubscriptionModel toModel(PushSubscription pushSubscription) {
        return PushSubscriptionModel
                .builder()
                .memberId(pushSubscription.getMemberId())
                .auth(pushSubscription.getAuth())
                .active(pushSubscription.isActive())
                .p256dh(pushSubscription.getP256dh())
                .endpoint(pushSubscription.getEndpoint())
                .userAgent(pushSubscription.getUserAgent())
                .createdAt(pushSubscription.getCreatedAt())
                .expirationTime(pushSubscription.getExpirationTime())
                .revokedAt(pushSubscription.getRevokedAt())
                .build();
    }

}
