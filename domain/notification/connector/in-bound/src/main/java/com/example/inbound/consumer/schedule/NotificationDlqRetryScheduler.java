package com.example.inbound.consumer.schedule;

import com.example.events.kafka.NotificationEvents;
import com.example.logging.MDC.KafkaMDCUtil;
import com.example.notification.model.FailMessageModel;
import com.example.notification.service.FailedMessageService;
import com.example.notification.service.WebPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Profile("!test")
@Component
@AllArgsConstructor
public class NotificationDlqRetryScheduler {

    private final FailedMessageService failedMessageService;
    @Qualifier("notificationKafkaTemplate")
    private final KafkaTemplate<String, NotificationEvents> kafkaTemplate;
    private final WebPushService webPushService;
    private final ObjectMapper objectMapper;
    private static final int MAX_RETRY_COUNT = 5;
    private static final String FINAL_TOPIC = "notification-events.retry.final";
    private static final long SEND_TIMEOUT_SECONDS = 10;
    private static final Set<String> HANDLED_TYPES = Set.of("NOTIFICATION", "WEB_PUSH");
    public static int EXECUTION_COUNT = 0;
    private final Counter retryCounter = Metrics.counter("kafka.actual.retry.count");

    @Timed(value = "kafka.dlq.retry.duration", description = "DLQ 재시도 처리 시간")
    @Scheduled(fixedDelay = 30 * 1000) // 10분(10*60*1000)에서 30초(30*1000)로 변경
    @SchedulerLock(name = "retryNotificationDlq", lockAtMostFor = "PT10M", lockAtLeastFor = "PT2S")
    public void retryNotifications() {

        // 알림 계열(NOTIFICATION, WEB_PUSH)만 처리. MEMBER_SIGNUP은 MemberSignUpDlqRetryScheduler 담당이며,
        // 여기서 함께 집으면 NotificationEvents 역직렬화 실패로 재시도 횟수가 이중으로 올라가거나
        // 두 스케줄러가 같은 행을 저장하면서 성공 처리가 덮어써진다.
        List<FailMessageModel> failMessageModels = failedMessageService
                .findReadyToRetry()
                .stream()
                .filter(e -> HANDLED_TYPES.contains(e.getMessageType()))
                .toList();
        log.info("List::"+failMessageModels);
        log.info("size:::"+failMessageModels.size());

        if (failMessageModels.isEmpty()) return;
        EXECUTION_COUNT++;
        log.info("실행됨: " + EXECUTION_COUNT);
        log.info("💡 DLQ 재처리 스케줄러 실행");
        for (FailMessageModel entity : failMessageModels) {

            if(entity.getRetryCount() >= MAX_RETRY_COUNT) {
                log.warn("재시도 초과 - id={}, payload={}", entity.getId(), entity.getPayload());
                entity.markAsDead();
                failedMessageService.updateFailMessage(entity);
                continue;
            }

            try {
                // WEB_PUSH는 별도 처리
                if ("WEB_PUSH".equals(entity.getMessageType())) {
                    NotificationEvents event = objectMapper
                            .readValue(entity.getPayload(), NotificationEvents.class);
                    webPushService.sendPush(event.getReceiverId(), event);
                    entity.resolveSuccess();
                    log.info("WebPush 재처리 성공 - id={}", entity.getId());
                } else {
                    NotificationEvents event = objectMapper.readValue(entity.getPayload(), NotificationEvents.class);
                    event.setForceSend(true);
                    KafkaMDCUtil.initMDC(event);
                    String retryTopic = getRetryTopicByCount(entity.getRetryCount());
                    // 브로커 응답까지 기다린다. 기다리지 않으면 전송이 실패해도 성공으로 기록된다.
                    kafkaTemplate.send(retryTopic, event.partitionKey(), event).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    retryCounter.increment(); // dlq가 정상 작동이 되었을때 카운트
                    log.info("재시도 메시지 전송: retryCount={}, topic={}", entity.getRetryCount(), retryTopic);
                    if (FINAL_TOPIC.equals(retryTopic)) {
                        // 최종 단계는 재전송하지 않고 포기 처리만 한다. 여기서 resolved=true로 저장하면
                        // retry.final 컨슈머가 먼저 남긴 dead=true를 덮어쓸 수 있어 스케줄러가 직접 dead로 둔다.
                        entity.markAsDead();
                    } else {
                        // 재발행까지 성공. 메인 컨슈머에서 다시 실패하면 DLQ 컨슈머가 이 행을 다시 연다(reopen).
                        entity.resolveSuccess();
                    }
                    log.info("DLQ 재처리 성공 - notification: id={}", entity.getId());
                }
            } catch (Exception ex) {
                entity.resolveFailure(ex);
                log.warn("DLQ 재처리 실패 - notification: id={}, reason={}", entity.getId(), ex.getMessage());
            } finally {
                KafkaMDCUtil.clear();
            }
            failedMessageService.updateFailMessage(entity);
        }
    }


    private String getRetryTopicByCount(int retryCount) {
        return switch (retryCount) {
            case 0 -> "notification-events.retry.5s";
            case 1 -> "notification-events.retry.10s";
            case 2 -> "notification-events.retry.30s";
            case 3 -> "notification-events.retry.60s";
            default -> FINAL_TOPIC;
        };
    }
}
