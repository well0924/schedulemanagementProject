package com.example.inbound.consumer.schedule;

import com.example.events.kafka.NotificationEvents;
import com.example.logging.MDC.KafkaMDCUtil;
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

    @KafkaListener(topics = "notification-events.retry.5s", groupId = "retry-group-5s")
    public void retry5s(NotificationEvents event) {
        try {
            KafkaMDCUtil.initMDC(event);
            kafkaTemplate.send("notification-events", event);
            log.info("5초 딜레이 후 재전송 완료: {}", event);
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "notification-events.retry.10s", groupId = "retry-group-10s")
    public void retry10s(NotificationEvents event) {
        try{
            KafkaMDCUtil.initMDC(event);
            kafkaTemplate.send("notification-events", event);
            log.info("10초 딜레이 후 재전송 완료: {}", event);
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "notification-events.retry.30s", groupId = "retry-group-30s")
    public void retry30s(NotificationEvents event) {
        try {
            KafkaMDCUtil.initMDC(event);
            kafkaTemplate.send("notification-events", event);
            log.info("30초 딜레이 후 재전송 완료: {}", event);
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "notification-events.retry.60s", groupId = "retry-group-60s")
    public void retry60s(NotificationEvents event) {
        try{
            KafkaMDCUtil.initMDC(event);
            kafkaTemplate.send("notification-events", event);
            log.info("60초 딜레이 후 재전송 완료: {}", event);

        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "notification-events.retry.final", groupId = "retry-group-final")
    public void retryFinal(NotificationEvents event) {
        try {
            KafkaMDCUtil.initMDC(event);
            // 마지막 실패 → 슬랙으로 연동하기.(추후 구현)
            log.warn("최종 재전송 도달 - 후속조치 필요: {}", event);
        } finally {
            KafkaMDCUtil.clear();
        }
    }
}
