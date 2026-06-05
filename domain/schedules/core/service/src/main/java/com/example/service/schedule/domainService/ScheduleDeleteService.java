package com.example.service.schedule.domainService;

import com.example.enumerate.schedules.DeleteType;
import com.example.events.enums.ScheduleActionType;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.interfaces.notification.notification.NotificationInterfaces;
import com.example.model.schedules.SchedulesModel;
import com.example.security.config.SecurityUtil;
import com.example.service.schedule.domainService.guard.ScheduleGuard;
import com.example.service.schedule.domainService.repeat.delete.RepeatDeleteRegistry;
import com.example.service.schedule.domainService.support.DomainEventPublisher;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class ScheduleDeleteService {

    private final ScheduleRepositoryPort scheduleRepositoryPort;
    private final ScheduleGuard scheduleGuard;
    private final RepeatDeleteRegistry repeatDeleteRegistry;
    private final NotificationInterfaces notificationInterfaces;
    private final DomainEventPublisher domainEventPublisher;

    public void deleteSchedule(Long scheduleId, DeleteType deleteType) {
        SchedulesModel target = scheduleRepositoryPort.findById(scheduleId);
        scheduleGuard.assertOwnerOrAdmin(target);

        List<SchedulesModel> deletedSchedules = repeatDeleteRegistry.dispatch(deleteType, target);
        notificationInterfaces.deleteReminderByScheduleId(scheduleId);
        domainEventPublisher.publish(deletedSchedules, ScheduleActionType.SCHEDULE_DELETE);
    }

    public void deleteSchedules(List<Long> ids) {
        Long me = SecurityUtil.currentUserId();
        List<Long> owned = scheduleRepositoryPort.findOwnedIds(me, ids);

        if (owned.size() != ids.size()) {
            throw new ScheduleCustomException(ScheduleErrorCode.INVALID_OWNER_FOR_BULK);
        }

        List<SchedulesModel> targets = scheduleRepositoryPort.findAllByIds(ids);
        scheduleRepositoryPort.markAsDeletedByIds(ids);
        domainEventPublisher.publish(targets, ScheduleActionType.SCHEDULE_DELETE);
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void deleteOldSchedules() {
        LocalDateTime thresholdDate = LocalDateTime.now().minusMonths(1);
        scheduleRepositoryPort.deleteOldSchedules(thresholdDate);
    }
}
