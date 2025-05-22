package com.example.inbound.consumer.schedule;

import com.example.events.kafka.NotificationEvents;
import com.example.interfaces.notification.kafka.KafkaDlqConsumer;
import com.example.notification.model.FailMessageModel;
import com.example.notification.service.FailedMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@AllArgsConstructor
public class DlqNotificationRetryConsumer implements KafkaDlqConsumer {

    private final FailedMessageService failedMessageService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "notification-events.DLQ", groupId = "dlq-retry-group")
    @Override
    public void consume(String message) {

        try{
            log.info("raw DLQ message: {}", message);
            NotificationEvents event = objectMapper.readValue(message, NotificationEvents.class);
            log.info("evemt:::"+event);
            String payload = objectMapper.writeValueAsString(event);
            log.info("DLQ → 객체 변환 완료: {}", payload);
            if (failedMessageService.findByPayload(payload)) return; // 중복 체크 추가

            FailMessageModel failMessageModel = FailMessageModel
                .builder()
                .topic("notification-events.DLQ".replace(".DLQ", ""))
                .messageType("NOTIFICATION")
                .payload(payload)
                .retryCount(0)
                .resolved(false)
                .exceptionMessage("자동 DLQ 저장")
                .createdAt(LocalDateTime.now())
                .build();

            failedMessageService.createFailMessage(failMessageModel);
            log.warn("DLQ 메시지 저장 완료: {}", payload);
        } catch (Exception e) {
            log.error("DLQ 메시지 역직렬화 또는 저장 실패", e);
        }
    }
}
