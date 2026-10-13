package com.example.kafka.consumer;

import com.example.events.enums.ScheduleActionType;
import com.example.events.kafka.NotificationEvents;
import com.example.inbound.consumer.schedule.DlqNotificationRetryConsumer;
import com.example.notification.model.FailMessageModel;
import com.example.notification.service.FailedMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

// 알림 DLQ 메시지를 저장할 때 재처리 예약 시각(nextRetryTime)을 넣는지 확인 (브로커 없이 단위 테스트)
// 스케줄러는 nextRetryTime <= now인 행만 조회하므로, null로 저장되면 한 번도 재처리되지 않는다.
class DlqNotificationRetryConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final FailedMessageService failedMessageService = mock(FailedMessageService.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);

    private final DlqNotificationRetryConsumer consumer =
            new DlqNotificationRetryConsumer(failedMessageService, objectMapper);

    @Test
    void DLQ_메시지는_재처리_예약시각을_넣어_저장한다() throws Exception {
        String message = objectMapper.writeValueAsString(NotificationEvents.builder()
                .receiverId(1L).scheduleId(10L).message("알림")
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .build());
        LocalDateTime before = LocalDateTime.now();

        consumer.consume(message, ack);

        ArgumentCaptor<FailMessageModel> captor = ArgumentCaptor.forClass(FailMessageModel.class);
        verify(failedMessageService).recordDlqFailure(captor.capture());
        FailMessageModel saved = captor.getValue();
        assertThat(saved.getNextRetryTime()).isNotNull();
        // 저장 직후가 아니라 몇 초 뒤에 스케줄러(30초 주기)가 집어 가도록 가까운 미래로 예약
        assertThat(saved.getNextRetryTime()).isAfter(before)
                .isBefore(LocalDateTime.now().plusSeconds(10));
        assertThat(saved.isResolved()).isFalse();
        assertThat(saved.isDead()).isFalse();
        verify(ack).acknowledge();
    }
}
