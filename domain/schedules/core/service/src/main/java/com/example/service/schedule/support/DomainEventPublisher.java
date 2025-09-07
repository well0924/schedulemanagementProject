package com.example.service.schedule.support;

import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.events.spring.ScheduleEvents;
import com.example.inbound.notification.NotificationSettingInConnector;
import com.example.model.schedules.SchedulesModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    private final NotificationSettingInConnector notificationSettingInConnector;


    public void publishScheduleEvent(SchedulesModel model, ScheduleActionType actionType, NotificationChannel channel) {
        ScheduleEvents event = ScheduleEvents.builder()
                .scheduleId(model.getId())
                .userId(model.getMemberId())
                .contents(model.getContents())
                .startTime(model.getStartTime())
                .notificationType(actionType)
                .notificationChannel(channel)
                .createdTime(LocalDateTime.now())
                .build();
        applicationEventPublisher.publishEvent(event);
    }

    public NotificationChannel resolveChannel(Long userId) {
        boolean webEnabled = notificationSettingInConnector.isEnabled(userId, NotificationChannel.WEB);
        log.info("webAlarm::"+webEnabled);
        boolean pushEnabled = notificationSettingInConnector.isEnabled(userId, NotificationChannel.PUSH);
        log.info("pushAlarm::"+pushEnabled);
        if (pushEnabled) {
            return NotificationChannel.PUSH; // PUSH 우선
        } else if (webEnabled) {
            return NotificationChannel.WEB;
        } else {
            return NotificationChannel.WEB; // 기본값
        }
    }

}
