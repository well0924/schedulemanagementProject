package com.example.outbound.notification;

import com.example.interfaces.notification.push.NotificationPushRepositoryPort;
import com.example.notification.mapper.NotificationEntityMapper;
import com.example.notification.mapper.NotificationMapper;
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
public class PushSubscriptionOutConnector implements NotificationPushRepositoryPort {

    private final PushSubscriptionRepository pushSubscriptionRepository;

    private final NotificationMapper notificationMapper;

    private final NotificationEntityMapper notificationEntityMapper;

    public List<PushSubscriptionModel> findByMemberIdAndActiveTrue(Long memberId) {
        return pushSubscriptionRepository
                .findByMemberIdAndActiveTrue(memberId)
                .stream()
                .map(notificationMapper::toModel)
                .collect(Collectors.toList());
    }

    public PushSubscriptionModel savePush(PushSubscriptionModel pushSubscriptionModel) {
        return notificationMapper
                .toModel(pushSubscriptionRepository
                        .save(notificationEntityMapper.toEntity(pushSubscriptionModel)));
    }

    public Optional<PushSubscriptionModel> findByUserIdAndEndpoint(Long memberId, String endpoint) {
        return pushSubscriptionRepository
                .findByMemberIdAndActiveTrue(memberId)
                .stream()
                .filter(sub -> sub.getEndpoint().equals(endpoint))
                .map(notificationMapper::toModel)
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

}
