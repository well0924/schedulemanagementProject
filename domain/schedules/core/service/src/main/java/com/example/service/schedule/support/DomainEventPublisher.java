package com.example.service.schedule.support;

import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.events.spring.ScheduleEvents;
import com.example.model.schedules.SchedulesModel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishScheduleEvent(SchedulesModel model, ScheduleActionType actionType) {
        ScheduleEvents event = ScheduleEvents.builder()
                .scheduleId(model.getId())
                .userId(model.getUserId())
                .contents(model.getContents())
                .notificationType(actionType)
                .notificationChannel(NotificationChannel.WEB)
                .createdTime(LocalDateTime.now())
                .build();
        applicationEventPublisher.publishEvent(event);
    }

}
