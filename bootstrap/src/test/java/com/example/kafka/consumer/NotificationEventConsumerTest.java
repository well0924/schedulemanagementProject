package com.example.kafka.consumer;

import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.events.kafka.NotificationEvents;
import com.example.events.process.ProcessedEventService;
import com.example.inbound.consumer.schedule.NotificationEventConsumer;
import com.example.notification.model.NotificationModel;
import com.example.notification.service.NotificationService;
import com.example.notification.service.NotificationSettingService;
import com.example.notification.service.WebPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 알림 컨슈머의 실패 처리 검증.
// 예전 코드는 일반 예외를 삼키고(재시도·DLQ 없이 유실), 처리 시작 시점에 Redis "processing" 키를 잡아
// 재시도가 들어와도 "이미 처리 중"으로 ack해 버렸다.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventConsumerTest {

    @Mock NotificationService notificationService;
    @Mock WebPushService webPushService;
    @Mock NotificationSettingService notificationSettingService;
    @Mock ProcessedEventService processedEventService;
    @Mock SimpMessagingTemplate simpMessagingTemplate;
    @Mock RedisTemplate redisTemplate;
    @Mock ValueOperations valueOperations;
    @Mock Acknowledgment ack;
    @Mock PlatformTransactionManager transactionManager;

    NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new NotificationEventConsumer(notificationService, webPushService, notificationSettingService,
                processedEventService, simpMessagingTemplate, objectMapper, redisTemplate,
                new TransactionTemplate(transactionManager));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(processedEventService.isAlreadyProcessed(anyString(), anyString())).thenReturn(false);
        when(notificationSettingService.isEnabled(anyLong(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("알림 저장이 실패하면 예외를 전파하고 ack·처리 기록·완료 표시를 남기지 않는다 (재시도 → DLQ로 가야 함)")
    void saveFails_propagatesWithoutAck() {
        NotificationEvents event = webEvent(ScheduleActionType.SCHEDULE_CREATED);
        doThrow(new RuntimeException("DB 저장 실패")).when(notificationService).createNotification(any());

        assertThatThrownBy(() -> consumer.handle(event, ack))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 저장 실패");

        verify(ack, never()).acknowledge();
        verify(processedEventService, never()).saveProcessedEvent(anyString(), anyString());
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        verify(simpMessagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    @DisplayName("재시도로 같은 메시지가 다시 와도 Redis 키가 없으니 다시 처리한다 (예전엔 processing 키 때문에 버려짐)")
    void retryAfterFailure_isProcessedAgain() {
        NotificationEvents event = webEvent(ScheduleActionType.SCHEDULE_CREATED);
        doThrow(new RuntimeException("일시 장애")).doReturn(null).when(notificationService).createNotification(any());

        assertThatThrownBy(() -> consumer.handle(event, ack)).isInstanceOf(RuntimeException.class);
        consumer.handle(event, ack);

        verify(notificationService, times(2)).createNotification(any());
        verify(processedEventService, times(1)).saveProcessedEvent("notification-group", event.getEventId());
        verify(ack, times(1)).acknowledge();
    }

    @Test
    @DisplayName("정상 처리: 내역 저장 → 처리 기록 → 실시간 전달 → 완료 표시 → ack")
    void success_savesDeliversAndAcks() {
        NotificationEvents event = webEvent(ScheduleActionType.SCHEDULE_CREATED);

        consumer.handle(event, ack);

        var inOrder = inOrder(notificationService, processedEventService, simpMessagingTemplate, valueOperations, ack);
        inOrder.verify(notificationService).createNotification(any(NotificationModel.class));
        inOrder.verify(processedEventService).saveProcessedEvent("notification-group", event.getEventId());
        inOrder.verify(simpMessagingTemplate).convertAndSend(eq("/topic/notifications/1"), anyString());
        inOrder.verify(valueOperations).set(eq("event:processed:" + event.getEventId()), eq("done"), any(Duration.class));
        inOrder.verify(ack).acknowledge();
    }

    @Test
    @DisplayName("저장은 트랜잭션 안에서, 전달·ack는 커밋이 끝난 뒤(트랜잭션 밖)에서 한다")
    void deliveryAndAck_happenAfterCommit() {
        NotificationEvents event = webEvent(ScheduleActionType.SCHEDULE_CREATED);

        consumer.handle(event, ack);

        var inOrder = inOrder(transactionManager, notificationService, processedEventService, simpMessagingTemplate, ack);
        inOrder.verify(transactionManager).getTransaction(any());
        inOrder.verify(notificationService).createNotification(any());
        inOrder.verify(processedEventService).saveProcessedEvent("notification-group", event.getEventId());
        inOrder.verify(transactionManager).commit(any());
        inOrder.verify(simpMessagingTemplate).convertAndSend(eq("/topic/notifications/1"), anyString());
        inOrder.verify(ack).acknowledge();
    }

    @Test
    @DisplayName("실시간 전달이 실패해도 내역은 저장됐으므로 ack한다")
    void deliveryFails_stillAcks() {
        NotificationEvents event = webEvent(ScheduleActionType.SCHEDULE_CREATED);
        doThrow(new RuntimeException("브로커 오류")).when(simpMessagingTemplate).convertAndSend(anyString(), any(Object.class));

        consumer.handle(event, ack);

        verify(processedEventService).saveProcessedEvent("notification-group", event.getEventId());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Redis에 완료 표시가 있으면 저장 없이 ack")
    void alreadyDoneInRedis_skips() {
        NotificationEvents event = webEvent(ScheduleActionType.SCHEDULE_CREATED);
        when(redisTemplate.hasKey("event:processed:" + event.getEventId())).thenReturn(true);

        consumer.handle(event, ack);

        verify(notificationService, never()).createNotification(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("DB에 처리 기록이 있으면 저장 없이 ack")
    void alreadyProcessedInDb_skips() {
        NotificationEvents event = webEvent(ScheduleActionType.SCHEDULE_CREATED);
        when(processedEventService.isAlreadyProcessed("notification-group", event.getEventId())).thenReturn(true);

        consumer.handle(event, ack);

        verify(notificationService, never()).createNotification(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("리마인드 웹 알림은 내역을 새로 저장하지 않고 웹푸시만 보낸다")
    void reminder_pushesWithoutSaving() {
        NotificationEvents event = webEvent(ScheduleActionType.SCHEDULE_REMINDER);

        consumer.handle(event, ack);

        verify(notificationService, never()).createNotification(any());
        verify(webPushService).sendPush(1L, event);
        verify(processedEventService).saveProcessedEvent("notification-group", event.getEventId());
        verify(ack).acknowledge();
    }

    private NotificationEvents webEvent(ScheduleActionType type) {
        return NotificationEvents.builder()
                .receiverId(1L)
                .scheduleId(10L)
                .message("알림")
                .notificationType(type)
                .notificationChannel(NotificationChannel.WEB)
                .createdTime(LocalDateTime.now())
                .build();
    }
}
