package com.example.inbound.consumer;

import com.example.events.NotificationEvents;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final SimpMessagingTemplate simpMessagingTemplate;

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "notification-events", groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(NotificationEvents event) {
        try {
            log.info("Kafka 알림 수신: {}", event);

            // NotificationEvents 객체를 JSON 문자열로 변환
            String message = objectMapper.writeValueAsString(event);
            log.debug(message);
            // WebSocket 클라이언트로 메시지 전송
            simpMessagingTemplate.convertAndSend(
                    "/topic/notifications/" + event.getReceiverId(),
                    message
            );

        } catch (JsonProcessingException e) {
            log.error("Kafka 메시지 직렬화 오류: {}", e.getMessage());
        }
    }

}
