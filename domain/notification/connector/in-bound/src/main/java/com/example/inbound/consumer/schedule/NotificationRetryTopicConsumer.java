package com.example.inbound.consumer.schedule;

import com.example.events.kafka.NotificationEvents;
import com.example.logging.MDC.KafkaMDCUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class NotificationRetryTopicConsumer {

    private final KafkaTemplate<String, NotificationEvents> kafkaTemplate;

    private final ObjectMapper objectMapper;

    //수동 측정하기.
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "notification-events.retry.5s", groupId = "retry-group-5s")
    public void retry5s(String message) {
        try {
            NotificationEvents event = objectMapper.readValue(message, NotificationEvents.class);
            meterRegistry.counter("kafka.retry.notification.success", "delay", "5s").increment();
            KafkaMDCUtil.initMDC(event);
            kafkaTemplate.send("notification-events", event);
            log.info("5초 딜레이 후 재전송 완료: {}", event);
        } catch (JsonProcessingException e) {
            meterRegistry.counter("kafka.retry.notification.failure", "delay", "5s").increment();
            log.error("Kafka DLQ 재처리 - 역직렬화 실패: {}", message, e);
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "notification-events.retry.10s", groupId = "retry-group-10s")
    public void retry10s(String message) {
        try{
            NotificationEvents event = objectMapper.readValue(message, NotificationEvents.class);
            meterRegistry.counter("kafka.retry.notification.success", "delay", "10s").increment();
            KafkaMDCUtil.initMDC(event);
            kafkaTemplate.send("notification-events", event);
            log.info("10초 딜레이 후 재전송 완료: {}", event);
        } catch (JsonProcessingException e) {
            meterRegistry.counter("kafka.retry.notification.failure", "delay", "10s").increment();
            log.error("Kafka DLQ 재처리 - 역직렬화 실패: {}", message, e);
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "notification-events.retry.30s", groupId = "retry-group-30s")
    public void retry30s(String message) {
        try {
            NotificationEvents event = objectMapper.readValue(message, NotificationEvents.class);
            meterRegistry.counter("kafka.retry.notification.success", "delay", "30s").increment();
            KafkaMDCUtil.initMDC(event);
            kafkaTemplate.send("notification-events", event);
            log.info("30초 딜레이 후 재전송 완료: {}", event);
        } catch (JsonProcessingException e) {
            meterRegistry.counter("kafka.retry.notification.failure", "delay", "30s").increment();
            log.error("Kafka DLQ 재처리 - 역직렬화 실패: {}", message, e);
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "notification-events.retry.60s", groupId = "retry-group-60s")
    public void retry60s(String message) {
        try{
            NotificationEvents event = objectMapper.readValue(message, NotificationEvents.class);
            meterRegistry.counter("kafka.retry.notification.success", "delay", "60s").increment();
            KafkaMDCUtil.initMDC(event);
            kafkaTemplate.send("notification-events", event);
            log.info("60초 딜레이 후 재전송 완료: {}", event);

        } catch (JsonProcessingException e) {
            meterRegistry.counter("kafka.retry.notification.failure", "delay", "60s").increment();
            log.error("Kafka DLQ 재처리 - 역직렬화 실패: {}", message, e);
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "notification-events.retry.final", groupId = "retry-group-final")
    public void retryFinal(String message) {
        try {
            NotificationEvents event = objectMapper.readValue(message, NotificationEvents.class);
            KafkaMDCUtil.initMDC(event);
            // 마지막 실패 → 슬랙으로 연동하기.(추후 구현)
            meterRegistry.counter("kafka.retry.notification.failure.final").increment();
            log.warn("최종 재전송 도달 - 후속조치 필요: {}", event);
        } catch (JsonProcessingException e) {
            log.error("Kafka DLQ 재처리 - 역직렬화 실패: {}", message, e);
        } finally {
            KafkaMDCUtil.clear();
        }
    }
}
