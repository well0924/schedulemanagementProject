package com.example.notification.service;

import com.example.events.NotificationChannel;
import com.example.events.NotificationEvents;
import com.example.events.ScheduleEvents;
import com.example.notification.NotificationType;

import com.example.notification.interfaces.NotificationEventInterfaces;
import com.example.notification.model.NotificationModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventEventListener implements NotificationEventInterfaces<ScheduleEvents> {

    private final NotificationService notificationService;
    
    private final NotificationEventProducer notificationEventProducer;

    @Async
    @EventListener
    public void handleSchedule(ScheduleEvents event) {
        log.info("event?");
        // 내부에서 추상화된 메서드 호출
        handle(event);
    }

    @Override
    public void handle(ScheduleEvents handle) {
        log.info("type:"+handle);
        NotificationChannel channel = Optional.ofNullable(handle.getNotificationChannel())
                .orElse(NotificationChannel.WEB);
        log.info("일정 이벤트 수신: {}", handle);
        // 채널에 따라 다르게 처리
        switch (channel) {
            case WEB -> handleWebNotification(handle);
            case PUSH -> handlePushNotification(handle);
        }
    }

    private void handleWebNotification(ScheduleEvents event) {
        try {
            // DB 저장
            notificationService.createNotification(toNotificationModel(event));
            // Kafka 발송
            notificationEventProducer.sendNotification(toNotificationEvent(event));
            log.info("Kafka 알림 이벤트 발송 성공: receiverId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Kafka 알림 이벤트 발송 실패: {}", e.getMessage());
        }
    }

    private void handlePushNotification(ScheduleEvents event) {
        log.info("푸시 알림 발송 준비 중: userId={}, scheduleId={}", event.getUserId(), event.getScheduleId());
        // 추후 구현 예정
    }

    private NotificationEvents toNotificationEvent(ScheduleEvents event) {
        return NotificationEvents.builder()
                .receiverId(event.getUserId())
                .message(buildMessage(event))
                .notificationType(mapActionToType(event.getActionType()).name())
                .createdTime(LocalDateTime.now())
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
            case "CREATE" -> "새로운 일정이 등록되었습니다.";
            case "UPDATE" -> "일정이 수정되었습니다.";
            case "DELETE" -> "일정이 삭제되었습니다.";
            default -> "일정에 변경이 있습니다.";
        };
    }

    private NotificationType mapActionToType(String action) {
        return switch (action) {
            case "CREATE" -> NotificationType.SCHEDULE_CREATED;
            case "UPDATE" -> NotificationType.SCHEDULE_UPDATED;
            case "DELETE" -> NotificationType.SCHEDULE_DELETED;
            default -> NotificationType.CUSTOM_NOTIFICATION;
        };
    }

}
