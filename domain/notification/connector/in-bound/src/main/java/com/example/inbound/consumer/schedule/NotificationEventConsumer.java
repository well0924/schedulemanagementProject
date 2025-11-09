package com.example.inbound.consumer.schedule;

import com.example.events.enums.NotificationChannel;
import com.example.events.kafka.NotificationEvents;
import com.example.events.process.ProcessedEventService;
import com.example.exception.dto.ErrorCode;
import com.example.exception.global.CustomExceptionHandler;
import com.example.interfaces.notification.kafka.KafkaEventConsumer;
import com.example.logging.MDC.KafkaMDCUtil;
import com.example.notification.NotificationType;
import com.example.notification.model.NotificationModel;
import com.example.notification.service.NotificationService;
import com.example.notification.service.NotificationSettingService;
import com.example.notification.service.WebPushService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer implements KafkaEventConsumer<NotificationEvents> {

    private final NotificationService notificationService;

    private final WebPushService webPushService;

    private final NotificationSettingService notificationSettingService;

    private final ProcessedEventService processedEventService;

    private final SimpMessagingTemplate simpMessagingTemplate;

    private final ObjectMapper objectMapper;

    @Timed(value = "kafka.consumer.notification.duration", description = "알림 Kafka 메시지 처리 시간")
    @Counted(value = "kafka.consumer.notification.count", description = "알림 Kafka 메시지 처리 횟수")
    @KafkaListener(
            topics = "notification-events",
            groupId = "notification-group",
            containerFactory = "notificationKafkaListenerFactory")
    public void handle(NotificationEvents event) {

        try {
            KafkaMDCUtil.initMDC(event);
            NotificationChannel channel = Optional.ofNullable(event.getNotificationChannel())
                    .orElse(NotificationChannel.WEB);

            // EOS 중복 체크
            if (processedEventService.isAlreadyProcessed(event.getEventId())) {
                log.info("⚠️ 이미 처리된 이벤트 무시: {}", event.getEventId());
                throw new CustomExceptionHandler("중복 이벤트 처리됨: " + event.getEventId(),
                        ErrorCode.EVENT_DUPLICATE);
            }
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
                    log.info("📩 Kafka 알림 수신: memberId={}, type={}, channel={}",
                            event.getReceiverId(), event.getNotificationType(), channel);
                    // dlq 처리시 조건 추가 (알림 구독여부)
                    boolean result = notificationSettingService.isEnabled(event.getReceiverId(), channel);
                    log.info("알림구독 여부:"+result);
                    if (!event.isForceSend() && !result) {
                        log.info("🔕 사용자 설정에 따라 알림 차단됨: userId={}, type={}, channel={}",
                                event.getReceiverId(), event.getNotificationType(), event.getNotificationChannel());
                        return;
                    }
                    //알림 내역 저장
                    handleWebNotification(event);
                    String message = objectMapper.writeValueAsString(event);
                    //알림 발송
                    simpMessagingTemplate.convertAndSend("/topic/notifications/" + event.getReceiverId(), message);
                }

                case PUSH -> {
                    log.info("📩 Kafka 알림 수신: memberId={}, type={}, channel={}",
                            event.getReceiverId(), event.getNotificationType(), channel);
                    //푸시 알림 내역 저장
                    handlePushNotification(event);
                    //푸시 알림 발송
                    webPushService.sendPush(event.getReceiverId(), event);
                }
            }
            // 처리 완료후 이벤트 저장
            processedEventService.saveProcessedEvent(event.getEventId());
        } catch (CustomExceptionHandler ex) {
            // 재처리 불필요 → 여기서 끝
            log.warn("[Kafka Non-Retry Error] code={}, msg={}", ex.getErrorCode(), ex.getMessage());
        } catch (JsonProcessingException e) {
            log.error("Kafka 메시지 직렬화 오류: {}", e.getMessage());
            throw new CustomExceptionHandler("이벤트 직렬화 실패: " + event.getEventId(), ErrorCode.EVENT_SERIALIZATION_ERROR);
        } catch (Exception e) {
            log.error("WebSocket 전송 실패", e);
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
        NotificationModel model = toNotificationModel(event);
        model.markAsSent();
        notificationService.createNotification(model);
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
