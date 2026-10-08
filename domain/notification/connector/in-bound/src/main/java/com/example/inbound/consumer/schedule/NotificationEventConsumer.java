package com.example.inbound.consumer.schedule;

import com.example.events.enums.NotificationChannel;
import com.example.events.kafka.NotificationEvents;
import com.example.events.process.ProcessedEventService;
import com.example.interfaces.notification.kafka.KafkaEventConsumer;
import com.example.logging.MDC.KafkaMDCUtil;
import com.example.notification.NotificationType;
import com.example.notification.model.NotificationModel;
import com.example.notification.service.NotificationService;
import com.example.notification.service.NotificationSettingService;
import com.example.notification.service.WebPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer implements KafkaEventConsumer<NotificationEvents> {

    // 중복 처리 판단 단위. 리스너 groupId와 같은 값이어야 한다.
    private static final String CONSUMER = "notification-group";

    // 처리가 끝난 이벤트만 표시하는 Redis 키. 처리 시작 시점에 잡지 않는다.
    // (예전엔 시작 시점에 "processing"으로 잡아, 처리 중 실패하거나 서버가 죽으면 재시도·재전달이 버려졌다)
    static final String DONE_KEY_PREFIX = "event:processed:";
    private static final Duration DONE_KEY_TTL = Duration.ofMinutes(10);

    private final NotificationService notificationService;

    private final WebPushService webPushService;

    private final NotificationSettingService notificationSettingService;

    private final ProcessedEventService processedEventService;

    private final SimpMessagingTemplate simpMessagingTemplate;

    private final ObjectMapper objectMapper;

    private final RedisTemplate redisTemplate;

    private final TransactionTemplate transactionTemplate;

    /**
     * 알림 내역과 처리 기록(processed_event)은 한 트랜잭션으로 저장하고,
     * 실시간 전달과 오프셋 커밋은 그 트랜잭션이 끝나 커넥션을 반납한 뒤에 한다.
     * (커밋 이후 콜백에서 하면 커넥션이 묶인 채라, 느린 작업이 커넥션을 붙잡고 DB 쓰기는 버려질 수 있다)
     * 저장 중 예외는 잡지 않고 전파해, 에러 핸들러의 재시도(2초 간격 3회) → DLQ로 가게 한다.
     */
    @Timed(value = "kafka.consumer.notification.duration", description = "알림 Kafka 메시지 처리 시간")
    @Counted(value = "kafka.consumer.notification.count", description = "알림 Kafka 메시지 처리 횟수")
    @KafkaListener(
            topics = "notification-events",
            groupId = CONSUMER,
            containerFactory = "notificationKafkaListenerFactory",
            concurrency = "3")
    @Override
    public void handle(NotificationEvents event, Acknowledgment ack) {

        try {
            KafkaMDCUtil.initMDC(event);

            NotificationChannel channel = Optional
                    .ofNullable(event.getNotificationChannel())
                    .orElse(NotificationChannel.WEB);

            String doneKey = DONE_KEY_PREFIX + event.getEventId();

            // 1. [Redis Pre-check] 처리가 끝난 이벤트면 DB 조회 전에 거른다
            if (Boolean.TRUE.equals(redisTemplate.hasKey(doneKey))) {
                log.info("🚀 [Redis Filter] 이미 처리된 이벤트: {}", event.getEventId());
                ack.acknowledge();
                return;
            }

            // 2. DB 기준 중복 확인
            if (processedEventService.isAlreadyProcessed(CONSUMER, event.getEventId())) {
                log.info("⚠️ 이미 처리된 이벤트 무시: {}", event.getEventId());
                ack.acknowledge();
                return;
            }
            log.info("📩 Kafka 알림 수신: userId={}, type={}, channel={}", event.getReceiverId(), event.getNotificationType(), channel);
            // dlq 처리시 조건 추가.
            if (!event.isForceSend() && !notificationSettingService.isEnabled(event.getReceiverId(), channel)) {
                log.info("🔕 사용자 설정에 따라 알림 차단됨: userId={}, type={}, channel={}", event.getReceiverId(), event.getNotificationType(), event.getNotificationChannel());
                ack.acknowledge();
                return;
            }

            // 3. 알림 내역 + 처리 기록 저장 (같은 트랜잭션)
            transactionTemplate.executeWithoutResult(status -> {
                saveNotification(event, channel);
                processedEventService.saveProcessedEvent(CONSUMER, event.getEventId());
            });

            // 4. 커밋·커넥션 반납 후: 실시간 전달 → 완료 표시 → 오프셋 커밋
            deliver(event, channel);
            markDone(doneKey);
            ack.acknowledge();
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    private void saveNotification(NotificationEvents event, NotificationChannel channel) {
        // 리마인드 웹 알림은 원본 알림 행이 이미 있어 추가 저장하지 않는다
        if (channel == NotificationChannel.WEB && isReminder(event)) {
            return;
        }
        NotificationModel model = toNotificationModel(event);
        // Kafka Consumer에서 전송 직후 저장이므로 isSent = true로 설정
        model.markAsSent();
        notificationService.createNotification(model);
    }

    /**
     * 실시간 전달은 재시도하지 않는다. 내역은 이미 저장됐고,
     * 재시도하면 처리 기록 때문에 어차피 건너뛰므로 실패는 로그로 남긴다.
     */
    private void deliver(NotificationEvents event, NotificationChannel channel) {
        try {
            switch (channel) {
                case WEB -> {
                    if (isReminder(event)) {
                        webPushService.sendPush(event.getReceiverId(), event);
                        log.info("🔔 리마인드 웹 알림 발송 완료: scheduleId={}", event.getScheduleId());
                        return;
                    }
                    String message = objectMapper.writeValueAsString(event);
                    simpMessagingTemplate.convertAndSend("/topic/notifications/" + event.getReceiverId(), message);
                }
                case PUSH -> webPushService.sendPush(event.getReceiverId(), event);
            }
        } catch (Exception e) {
            log.error("❌ 알림 실시간 전달 실패 (내역은 저장됨): eventId={}, channel={}", event.getEventId(), channel, e);
        }
    }

    // Redis 표시는 DB 조회를 줄이는 용도라, 실패해도 DB 처리 기록이 중복을 막는다
    private void markDone(String doneKey) {
        try {
            redisTemplate.opsForValue().set(doneKey, "done", DONE_KEY_TTL);
        } catch (Exception e) {
            log.warn("Redis 처리 완료 표시 실패 (DB 처리 기록으로 중복 방지): key={}", doneKey, e);
        }
    }

    private boolean isReminder(NotificationEvents event) {
        return mapActionToType(event.getNotificationType().name()) == NotificationType.SCHEDULE_REMINDER;
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
                .isReminderSent(false)
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
