package com.example.inbound.consumer.schedule;

import com.example.events.enums.NotificationChannel;
import com.example.events.kafka.NotificationEvents;
import com.example.events.spring.ScheduleEvents;
import com.example.notification.NotificationType;
import com.example.notification.model.NotificationModel;
import com.example.notification.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    private final SimpMessagingTemplate simpMessagingTemplate;

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "notification-events",
            groupId = "notification-group",
            containerFactory = "notificationKafkaListenerFactory")
    public void consume(NotificationEvents event) {
        // 테스트용 예외 강제 발생
//        if ("DLQ 테스트".equals(event.getMessage())) {
//            throw new RuntimeException("강제 예외 발생 - DLQ로 이동 테스트");
//        }
        try {
            log.info("Kafka 알림 수신: {}", event);
            NotificationChannel channel = Optional.ofNullable(event.getNotificationChannel())
                    .orElse(NotificationChannel.WEB);

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
        }
    }


    private void handleWebNotification(NotificationEvents event) {
        // DB 저장
        notificationService.createNotification(toNotificationModel(event));
    }

    private void handlePushNotification(NotificationEvents event) {
        // 추후 구현 예정
    }

    private NotificationModel toNotificationModel(NotificationEvents event) {
        return NotificationModel.builder()
                .userId(event.getReceiverId())
                .message(event.getMessage())
                .createdTime(event.getCreatedTime())
                .notificationType(mapActionToType(event.getNotificationType().name()))
                .isRead(false)
                .isSent(false)
                .build();
    }

    private NotificationModel toNotificationModel(ScheduleEvents event) {
        return NotificationModel.builder()
                .userId(event.getUserId())
                .scheduleId(event.getScheduleId())
                .message(buildMessage(event))
                .notificationType(mapActionToType(event.getActionType()))
                .isRead(false)
                .isSent(false)
                .scheduledAt(LocalDateTime.now())
                .build();
    }

    private String buildMessage(ScheduleEvents event) {
        return switch (event.getActionType()) {
            case "SCHEDULE_CREATED" -> "새로운 일정이 등록되었습니다.";
            case "SCHEDULE_UPDATED" -> "일정이 수정되었습니다.";
            case "SCHEDULE_DELETED" -> "일정이 삭제되었습니다.";
            default -> "일정에 변경이 있습니다.";
        };
    }

    private NotificationType mapActionToType(String actionType) {
        if (actionType == null) return NotificationType.CUSTOM_NOTIFICATION;
        return switch (actionType) {
            case "SCHEDULE_CREATED" -> NotificationType.SCHEDULE_CREATED;
            case "SCHEDULE_UPDATED" -> NotificationType.SCHEDULE_UPDATED;
            case "SCHEDULE_DELETED" -> NotificationType.SCHEDULE_DELETED;
            default -> NotificationType.CUSTOM_NOTIFICATION;
        };
    }
}
