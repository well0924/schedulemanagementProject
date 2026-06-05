package com.example.service.schedule.domainService.support;

import com.example.events.enums.ScheduleActionType;
import com.example.events.spring.ScheduleDomainEvent;
import com.example.model.schedules.SchedulesModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publish(List<SchedulesModel> schedules, ScheduleActionType actionType) {
        if (schedules == null || schedules.isEmpty()) {
            return;
        }
        log.info("스프링 도메인 이벤트 발행 시작: action={}, count={}", actionType, schedules.size());
        eventPublisher.publishEvent(new ScheduleDomainEvent(schedules, actionType));
    }
}
