package com.example.inbound.consumer.schedule;

import com.example.events.kafka.NotificationEvents;
import com.example.notification.model.FailMessageModel;
import com.example.notification.service.FailedMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@AllArgsConstructor
public class NotificationDlqRetryScheduler {

    private final FailedMessageService failedMessageService;
    private final KafkaTemplate<String, NotificationEvents> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final int MAX_RETRY_COUNT = 5;


    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void retryNotifications() {
        log.info("💡 DLQ 재처리 스케줄러 실행");
        List<FailMessageModel> failMessageModels = failedMessageService
                .findByResolvedFalse()
                .stream()
                .toList();
        log.info("List::"+failMessageModels);
        log.info("size:::"+failMessageModels.size());
        for (FailMessageModel entity : failMessageModels) {

            if(entity.getRetryCount() >= MAX_RETRY_COUNT) {
                log.warn("재시도 초과 - id={}, payload={}", entity.getId(), entity.getPayload());
                entity.setDead();
                entity.setLastTriedAt();
                failedMessageService.updateFailMessage(entity);
                continue;
            }

            try {
                NotificationEvents event = objectMapper.readValue(entity.getPayload(), NotificationEvents.class);
                String retryTopic = getRetryTopicByCount(entity.getRetryCount());
                kafkaTemplate.send(retryTopic, event);
                log.info("재시도 메시지 전송: retryCount={}, topic={}", entity.getRetryCount(), retryTopic);
                // resolved를 true로 변환
                entity.setResolved();
                log.info("DLQ 재처리 성공 - notification: id={}", entity.getId());
            } catch (Exception ex) {
                entity.setIncresementRetryCount();
                entity.setLastTriedAt();
                entity.setExceptionMessage(ex.getMessage());
                log.warn("DLQ 재처리 실패 - notification: id={}, reason={}", entity.getId(), ex.getMessage());
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
