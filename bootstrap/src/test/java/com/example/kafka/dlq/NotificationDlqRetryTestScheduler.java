package com.example.kafka.dlq;

import com.example.events.kafka.NotificationEvents;
import com.example.logging.MDC.KafkaMDCUtil;
import com.example.notification.model.FailMessageModel;
import com.example.notification.service.FailedMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Profile("test")
@Component
public class NotificationDlqRetryTestScheduler {

    private final FailedMessageService failedMessageService;
    private final KafkaTemplate<String, NotificationEvents> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final int MAX_RETRY_COUNT = 5;

    public NotificationDlqRetryTestScheduler(
            FailedMessageService failedMessageService,
            @Qualifier("testNotificationKafkaTemplate") KafkaTemplate<String, NotificationEvents> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.failedMessageService = failedMessageService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }


    @Scheduled(fixedDelay = 30 * 1000)
    @SchedulerLock(name = "retryNotificationDlq", lockAtMostFor = "PT10M", lockAtLeastFor = "PT2S")
    public void retryNotifications() {
        log.info("💡 DLQ 재처리 스케줄러 실행");
        List<FailMessageModel> failMessageModels = failedMessageService
                .findReadyToRetry()
                .stream()
                .filter(e -> "NOTIFICATION".equals(e.getMessageType()))
                .toList();
        log.info("List::"+failMessageModels);
        log.info("size:::"+failMessageModels.size());
        for (FailMessageModel entity : failMessageModels) {

            if(entity.getRetryCount() >= MAX_RETRY_COUNT) {
                log.warn("재시도 초과 - id={}, payload={}", entity.getId(), entity.getPayload());
                entity.markAsDead();
                failedMessageService.updateFailMessage(entity);
                continue;
            }

            try {
                NotificationEvents event = objectMapper.readValue(entity.getPayload(), NotificationEvents.class);
                event.setForceSend(true);
                KafkaMDCUtil.initMDC(event);
                String retryTopic = getRetryTopicByCount(entity.getRetryCount());
                kafkaTemplate.send(retryTopic, event);
                log.info("재시도 메시지 전송: retryCount={}, topic={}", entity.getRetryCount(), retryTopic);
                // resolved를 true로 변환
                entity.resolveSuccess();
                log.info("DLQ 재처리 성공 - notification: id={}", entity.getId());
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
            default -> "notification-events.retry.final";
        };
    }
}
