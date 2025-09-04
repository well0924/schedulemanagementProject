package com.example.notification.service;

import com.example.events.kafka.NotificationEvents;
import com.example.notification.model.PushSubscriptionModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.PushService;
import org.springframework.stereotype.Service;

import nl.martijndwars.webpush.Notification;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class WebPushService {

    private final PushSubscriptionService subscriptionService;

    private final PushService pushService;

    private final ObjectMapper objectMapper;

    public void sendPush(Long memberId, NotificationEvents event) {

        List<PushSubscriptionModel> subs = subscriptionService.getActiveSubscriptions(memberId);

        if (subs.isEmpty()) {
            log.warn("⚠️ No active webpush subscriptions found for memberId={}", memberId);
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("WebPushService error - memberId={}", memberId, e);
            return;
        }

        for (PushSubscriptionModel sub : subs) {
            try {
                Notification notification = new Notification(
                        sub.getEndpoint(),
                        sub.getP256dh(),
                        sub.getAuth(),
                        payload
                );
                pushService.send(notification);
                log.info(" WebPush 발송 성공 - endpoint={}", sub.getEndpoint());
            } catch (Exception e) {
                log.error(" WebPush 발송 실패 - endpoint={}", sub.getEndpoint(), e);
            }
        }
    }

}
