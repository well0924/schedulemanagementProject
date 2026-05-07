package com.example.notification.service;

import com.example.events.kafka.NotificationEvents;
import com.example.notification.model.FailMessageModel;
import com.example.notification.model.PushSubscriptionModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.PushService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import nl.martijndwars.webpush.Notification;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class WebPushService {

    private final PushSubscriptionService subscriptionService;

    private final PushService pushService;

    private final FailedMessageService failedMessageService;

    private final ObjectMapper objectMapper;

    @Async("threadPoolTaskExecutor")
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

        // 비동기로 처리를 한 경우에는 메인 스레드나 DB커넥션에 영향이 없음.
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
                try {
                    failedMessageService.createFailMessage(
                            FailMessageModel.builder()
                                    .topic("web-push")
                                    .messageType("WEB_PUSH")
                                    .payload(payload)  // 이미 직렬화된 거 재사용
                                    .retryCount(0)
                                    .resolved(false)
                                    .eventId(event.getEventId())
                                    .exceptionMessage(e.getMessage())
                                    .nextRetryTime(LocalDateTime.now().plusSeconds(30)) // 추가
                                    .createdAt(LocalDateTime.now())
                                    .build()
                    );
                } catch (Exception saveEx) {
                    log.error("FailMessage 저장도 실패 - eventId={}", event.getEventId(), saveEx);
                }
            }
        }
    }

}
