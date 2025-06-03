package com.example.notification.service;

import com.example.events.enums.ScheduleActionType;
import com.example.events.kafka.NotificationEvents;
import com.example.events.spring.ScheduleEvents;
import com.example.interfaces.notification.event.NotificationEventInterfaces;
import com.example.outbound.producer.NotificationEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventEventListener implements NotificationEventInterfaces<ScheduleEvents> {
    
    private final NotificationEventProducer notificationEventProducer;

    @Async
    @EventListener
    public void handleSchedule(ScheduleEvents event) {
        log.info("event?");
        handle(event);
    }

    @Override
    public void handle(ScheduleEvents handle) {
        log.info("type:"+handle);
        // 알림 이벤트 발송.
        NotificationEvents notificationEvents = NotificationEvents
                .builder()
                .receiverId(handle.getUserId())
                .message(handle.getContents())
                .notificationType(ScheduleActionType.valueOf(handle.getActionType()))
                .createdTime(handle.getCreatedTime())
                .build();
        //프로듀서 전송
        notificationEventProducer.sendNotification(notificationEvents);
    }
}
