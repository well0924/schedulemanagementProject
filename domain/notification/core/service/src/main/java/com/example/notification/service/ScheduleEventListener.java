package com.example.notification.service;

import com.example.events.enums.AggregateType;
import com.example.events.kafka.NotificationEvents;
import com.example.events.outbox.OutboxEventService;
import com.example.events.spring.ScheduleEvents;
import com.example.interfaces.notification.event.NotificationEventInterfaces;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleEventListener implements NotificationEventInterfaces<ScheduleEvents> {

    private final OutboxEventService outboxEventService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Override
    public void handle(ScheduleEvents handle) {
        log.info("event?");
        NotificationEvents notificationEvents = NotificationEvents.of(handle);
        log.info(notificationEvents.getMessage());
        //아웃박스 적용
        outboxEventService.saveEvent(notificationEvents,
                AggregateType.SCHEDULE.name(),
                handle.getScheduleId().toString(),
                notificationEvents.getNotificationType().name());
    }
}
