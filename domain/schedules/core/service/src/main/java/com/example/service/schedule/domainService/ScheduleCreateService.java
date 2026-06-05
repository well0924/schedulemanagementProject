package com.example.service.schedule.domainService;

import com.example.enumerate.schedules.RepeatType;
import com.example.events.enums.ScheduleActionType;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.interfaces.notification.notification.NotificationInterfaces;
import com.example.model.schedules.SchedulesModel;
import com.example.security.config.SecurityUtil;
import com.example.service.schedule.domainService.guard.ScheduleGuard;
import com.example.service.schedule.domainService.repeat.create.RepeatScheduleFactory;
import com.example.service.schedule.domainService.support.AttachBinder;
import com.example.service.schedule.domainService.support.DomainEventPublisher;
import com.example.service.schedule.domainService.support.ScheduleClassifier;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@Transactional
@AllArgsConstructor
public class ScheduleCreateService {

    private final ScheduleRepositoryPort scheduleRepositoryPort;
    private final RepeatScheduleFactory repeatScheduleFactory;
    private final ScheduleClassifier scheduleClassifier;
    private final ScheduleGuard scheduleGuard;
    private final AttachBinder attachBinder;
    private final NotificationInterfaces notificationInterfaces;
    private final DomainEventPublisher domainEventPublisher;

    public SchedulesModel saveSchedule(SchedulesModel model) {
        // 반복 여부에 따른 스케줄 생성
        List<SchedulesModel> schedulesToSave = (model.getRepeatType() == RepeatType.NONE || model.getRepeatCount() == null || model.getRepeatCount() <= 0)
                ? List.of(model)
                : repeatScheduleFactory.generateRepeatedSchedules(model);

        if (schedulesToSave.isEmpty()) {
            throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_CREATED_FAIL);
        }

        Long currentMemberId = SecurityUtil.currentUserId();

        // 가공 및 정렬
        List<SchedulesModel> processedSchedules = schedulesToSave.stream()
                .map(m -> m.toBuilder()
                        .scheduleType(scheduleClassifier.classify(m))
                        .memberId(currentMemberId)
                        .build())
                .sorted(Comparator.comparing(SchedulesModel::getStartTime))
                .toList();

        // 검증 및 저장
        validateBulkConflict(processedSchedules);
        scheduleGuard.validateCreation(processedSchedules);

        List<SchedulesModel> savedSchedules = scheduleRepositoryPort.saveAll(processedSchedules);
        SchedulesModel firstSchedule = savedSchedules.get(0);

        // 첨부파일 바인딩
        if (model.getAttachIds() != null && !model.getAttachIds().isEmpty()) {
            attachBinder.bindToSchedule(model.getAttachIds(), firstSchedule.getId());
            firstSchedule = firstSchedule.toBuilder()
                    .attachIds(model.getAttachIds())
                    .build();
        }

        // 리마인드 알림 및 이벤트 발행.
        notificationInterfaces.createReminder(firstSchedule);
        domainEventPublisher.publish(List.of(firstSchedule), ScheduleActionType.SCHEDULE_CREATED);
        return firstSchedule;
    }

    private void validateBulkConflict(List<SchedulesModel> newSchedules) {
        LocalDateTime minStart = newSchedules.stream().map(SchedulesModel::getStartTime).min(LocalDateTime::compareTo).get();
        LocalDateTime maxEnd = newSchedules.stream().map(SchedulesModel::getEndTime).max(LocalDateTime::compareTo).get();

        long isConflictCount = scheduleRepositoryPort.findOverlappingSchedulesInRange(
                newSchedules.get(0).getMemberId(), minStart, maxEnd);

        if (isConflictCount > 0) throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_TIME_CONFLICT);
    }
}
