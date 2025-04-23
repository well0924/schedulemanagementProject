package com.example.notification.service;

import com.example.notification.apimodel.NotificationEvents;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class NotificationEventProducer {

    private final KafkaTemplate<String, NotificationEvents> kafkaTemplate;

    private static final String TOPIC_NAME = "notification-events"; // Kafka 토픽명

    public void sendNotification(NotificationEvents event) {
        log.info("Kafka 이벤트 발송: topic={}, event={}", TOPIC_NAME, event);
        kafkaTemplate.send(TOPIC_NAME, event);
    }
}
