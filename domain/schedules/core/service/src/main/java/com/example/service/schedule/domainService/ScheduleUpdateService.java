package com.example.service.schedule.domainService;

import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.enumerate.schedules.RepeatUpdateType;
import com.example.events.enums.ScheduleActionType;
import com.example.inbound.schedules.ScheduleRepositoryPort;
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
    private final DomainEventPublisher domainEventPublisher;

    public SchedulesModel updateSchedule(Long scheduleId, SchedulesModel model, RepeatUpdateType updateType) {
        SchedulesModel existing = scheduleRepositoryPort.findById(scheduleId);
        scheduleGuard.assertOwnerOrAdmin(existing);

        RepeatUpdateType t = Optional.ofNullable(updateType).orElse(RepeatUpdateType.SINGLE);
        List<SchedulesModel> result = repeatUpdateRegistry.dispatch(t, existing, model);

        // 리마인드 알림은 ScheduleEventListener의 AFTER_COMMIT 단계에서 처리 (2026-09-11)
        domainEventPublisher.publish(result, ScheduleActionType.SCHEDULE_UPDATE);
        return result.get(0);
    }

    public PROGRESS_STATUS updateProgressStatus(Long scheduleId, PROGRESS_STATUS newStatus) {
        // 존재 확인(없으면 SCHEDULE_NOT_FOUND) + 소유자/관리자만 상태 변경 가능
        SchedulesModel existing = scheduleRepositoryPort.findById(scheduleId);
        scheduleGuard.assertOwnerOrAdmin(existing);

        scheduleRepositoryPort.updateStatusOnly(scheduleId, newStatus);
        return newStatus;
    }
}
