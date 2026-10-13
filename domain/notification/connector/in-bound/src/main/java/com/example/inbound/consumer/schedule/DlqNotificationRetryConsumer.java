package com.example.inbound.consumer.schedule;

import com.example.events.kafka.NotificationEvents;
import com.example.interfaces.notification.kafka.KafkaDlqConsumer;
import com.example.logging.MDC.KafkaMDCUtil;
import com.example.notification.model.FailMessageModel;
import com.example.notification.service.FailedMessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@AllArgsConstructor
public class DlqNotificationRetryConsumer implements KafkaDlqConsumer {

    private final FailedMessageService failedMessageService;
    private final ObjectMapper objectMapper;

    @Timed(value = "kafka.dlq.notification.save.duration", description = "알림 DLQ 저장 처리 시간")
    @KafkaListener(topics = "notification-events.DLQ", groupId = "dlq-retry-group")
    @Override
    public void consume(String message, Acknowledgment ack) {

        try{
            log.info("raw DLQ message: {}", message);
            NotificationEvents event = objectMapper.readValue(message, NotificationEvents.class);
            log.info("evemt:::"+event);
            KafkaMDCUtil.initMDC(event);
            String payload = objectMapper.writeValueAsString(event);
            log.info("DLQ → 객체 변환 완료: {}", payload);
            // 실패 내역 저장 (첫 실패면 새로 저장, 재발행 후 다시 실패했으면 기존 행을 다시 연다)
            FailMessageModel failMessageModel = FailMessageModel
                .builder()
                .topic("notification-events.DLQ".replace(".DLQ", ""))
                .messageType("NOTIFICATION")
                .payload(payload)
                .retryCount(0)
                .resolved(false)
                .eventId(event.getEventId())
                .exceptionMessage("자동 DLQ 저장")
                .createdAt(LocalDateTime.now())
                // 스케줄러는 nextRetryTime <= now인 행만 집는다. 비워 두면 영원히 재처리되지 않는다.
                .nextRetryTime(LocalDateTime.now().plusSeconds(5))
                .build();

            failedMessageService.recordDlqFailure(failMessageModel);
            log.warn("DLQ 메시지 기록 완료: {}", payload);
            // 실패 내역 저장 성공 시 커밋
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            // 역직렬화 불가 메시지는 재시도해도 실패하므로 커밋하고 넘어간다
            ack.acknowledge();
            log.error("DLQ 메시지 역직렬화 실패: {}", message, e);
        } catch (Exception e) {
            log.error("DLQ 메시지 저장 실패", e);
        } finally {
            KafkaMDCUtil.clear();
        }
    }
}
