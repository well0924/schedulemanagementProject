package com.example.service.schedule.domainService;

import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.enumerate.schedules.RepeatUpdateType;
import com.example.events.enums.ScheduleActionType;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.interfaces.notification.notification.NotificationInterfaces;
import com.example.model.schedules.SchedulesModel;
import com.example.service.schedule.domainService.guard.ScheduleGuard;
import com.example.service.schedule.domainService.repeat.update.RepeatUpdateRegistry;
import com.example.service.schedule.domainService.support.DomainEventPublisher;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@AllArgsConstructor
public class ScheduleUpdateService {

    private final ScheduleRepositoryPort scheduleRepositoryPort;
    private final ScheduleGuard scheduleGuard;
    private final RepeatUpdateRegistry repeatUpdateRegistry;
    private final NotificationInterfaces notificationInterfaces;
    private final DomainEventPublisher domainEventPublisher;

    public SchedulesModel updateSchedule(Long scheduleId, SchedulesModel model, RepeatUpdateType updateType) {
        SchedulesModel existing = scheduleRepositoryPort.findById(scheduleId);
        scheduleGuard.assertOwnerOrAdmin(existing);

        RepeatUpdateType t = Optional.ofNullable(updateType).orElse(RepeatUpdateType.SINGLE);
        List<SchedulesModel> result = repeatUpdateRegistry.dispatch(t, existing, model);

        notificationInterfaces.createReminder(result.get(0));
        domainEventPublisher.publish(result, ScheduleActionType.SCHEDULE_UPDATE);
        return result.get(0);
    }

    public PROGRESS_STATUS updateProgressStatus(Long scheduleId, PROGRESS_STATUS newStatus) {
        scheduleRepositoryPort.updateStatusOnly(scheduleId, newStatus);
        return newStatus;
    }
}
