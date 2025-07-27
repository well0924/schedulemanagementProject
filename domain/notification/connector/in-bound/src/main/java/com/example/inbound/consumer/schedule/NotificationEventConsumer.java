package com.example.inbound.consumer.schedule;

import com.example.events.enums.NotificationChannel;
import com.example.events.kafka.NotificationEvents;
import com.example.logging.MDC.KafkaMDCUtil;
import com.example.notification.NotificationType;
import com.example.notification.model.NotificationModel;
import com.example.notification.service.NotificationService;
import com.example.notification.service.NotificationSettingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    private final NotificationSettingService notificationSettingService;

    private final SimpMessagingTemplate simpMessagingTemplate;

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "notification-events",
            groupId = "notification-group",
            containerFactory = "notificationKafkaListenerFactory")
    public void consume(NotificationEvents event) {

        KafkaMDCUtil.initMDC(event);
        NotificationChannel channel = Optional.ofNullable(event.getNotificationChannel())
                .orElse(NotificationChannel.WEB);

        try {
            log.info("📩 Kafka 알림 수신: userId={}, type={}, channel={}",
                    event.getReceiverId(), event.getNotificationType(), channel);
            // dlq 처리시 조건 추가.
            if (!event.isForceSend() && !notificationSettingService.isEnabled(
                    event.getReceiverId(),
                    channel)
            ) {
                log.info("🔕 사용자 설정에 따라 알림 차단됨: userId={}, type={}, channel={}",
                        event.getReceiverId(), event.getNotificationType(), event.getNotificationChannel());
                return;
            }

            switch (channel) {
                case WEB -> {
                    //알림 내역 저장
                    handleWebNotification(event);
                    String message = objectMapper.writeValueAsString(event);
                    //알림 발송
                    simpMessagingTemplate.convertAndSend(
                            "/topic/notifications/" + event.getReceiverId(),
                            message
                    );
                }
                case PUSH -> {
                    //알림 내역 저장
                    handlePushNotification(event);
                    String message = objectMapper.writeValueAsString(event);
                    //알림 발송
                    simpMessagingTemplate.convertAndSend(
                            "/topic/notifications/" + event.getReceiverId(),
                            message
                    );
                }
            }

        } catch (JsonProcessingException e) {
            log.error("Kafka 메시지 직렬화 오류: {}", e.getMessage());
        } catch (Exception e) {
            log.error("WebSocket 전송 실패", e);
            throw new RuntimeException("WebSocket send failed", e); // DLQ 트리거
        } finally {
            KafkaMDCUtil.clear();
        }
    }


    private void handleWebNotification(NotificationEvents event) {
        // DB 저장
        NotificationModel model = toNotificationModel(event);

        // Kafka Consumer에서 전송 직후 저장이므로 isSent = true로 설정
        model.markAsSent();
        notificationService.createNotification(model);
    }

    private void handlePushNotification(NotificationEvents event) {
        // 추후 구현 예정
    }

    private NotificationModel toNotificationModel(NotificationEvents event) {
        return NotificationModel.builder()
                .userId(event.getReceiverId())
                .scheduleId(event.getScheduleId())
                .message(event.getMessage())
                .createdTime(event.getCreatedTime())
                .notificationType(mapActionToType(event.getNotificationType().name()))
                .scheduledAt(event.getScheduleAt())
                .isRead(false)
                .isSent(false)
                .build();
    }

    private NotificationType mapActionToType(String actionType) {
        if (actionType == null) return NotificationType.CUSTOM_NOTIFICATION;
        return switch (actionType) {
            case "SCHEDULE_CREATED" -> NotificationType.SCHEDULE_CREATED;
            case "SCHEDULE_UPDATED" -> NotificationType.SCHEDULE_UPDATED;
            case "SCHEDULE_DELETED" -> NotificationType.SCHEDULE_DELETED;
            case "SCHEDULE_REMINDER" -> NotificationType.SCHEDULE_REMINDER;
            default -> NotificationType.CUSTOM_NOTIFICATION;
        };
    }
}
