package com.example.notification.service;

import com.example.events.NotificationChannel;
import com.example.events.ScheduleEvents;
import com.example.notification.NotificationType;
import com.example.notification.apimodel.NotificationEvents;
import com.example.notification.interfaces.NotificationEventInterfaces;
import com.example.notification.model.NotificationModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventEventListener implements NotificationEventInterfaces<ScheduleEvents> {

    private final NotificationService notificationService;
    
    private final NotificationEventProducer notificationEventProducer;

    @Async
    @EventListener
    @Override
    public void handle(ScheduleEvents event) {
        log.info("일정 이벤트 수신: {}", event);

        // null 체크 후 디폴트 채널 세팅
        NotificationChannel channel = event.getNotificationChannel() != null
                ? event.getNotificationChannel()
                : NotificationChannel.WEB; // 기본값: 웹 알림

        // 채널에 따라 다르게 처리
        switch (channel) {
            case WEB -> {
                //알림 발송(일정)
                try {
                    NotificationEvents kafkaEvent = NotificationEvents.builder()
                            .receiverId(event.getUserId())
                            .message(buildMessage(event))
                            .notificationType(mapActionToType(event.getActionType()).name()) // ENUM을 문자열로 변환
                            .createdTime(LocalDateTime.now())
                            .build();
                    //알림 발송
                    notificationEventProducer.sendNotification(kafkaEvent);

                    NotificationModel notificationModel = NotificationModel
                            .builder()
                            .userId(event.getUserId())
                            .scheduleId(event.getScheduleId())
                            .scheduledAt(LocalDateTime.now())
                            .message(buildMessage(event))
                            .notificationType(mapActionToType(event.getActionType()))
                            .isRead(false)
                            .isSent(false)
                            .build();

                    //알림 저장
                    notificationService.createNotification(notificationModel);

                    log.info("Kafka 알림 이벤트 발송 성공: receiverId={}", event.getUserId());
                } catch (Exception e) {
                    log.error("Kafka 알림 이벤트 발송 실패: {}", e.getMessage());
                }
            }
            //추후에 확장예정.
            case PUSH -> {
                log.info("푸시 알림 발송 준비 중: userId={}, scheduleId={}", event.getUserId(), event.getScheduleId());
            }
        }
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
