package com.example.service.schedule;

import com.example.enumerate.schedules.*;
import com.example.events.enums.ScheduleActionType;
import com.example.events.enums.NotificationChannel;
import com.example.events.spring.ScheduleEvents;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import com.example.inbound.attach.AttachInConnector;
import com.example.inbound.notification.NotificationInConnector;
import com.example.model.schedules.SchedulesModel;
import com.example.outbound.schedule.ScheduleOutConnector;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@AllArgsConstructor
public class ScheduleDomainService {

    private final ScheduleOutConnector scheduleOutConnector;

    private final AttachInConnector attachInConnector;

    private final NotificationInConnector notificationInConnector;

    private final ApplicationEventPublisher applicationEventPublisher;

    public List<SchedulesModel> getAllSchedules() {
        return scheduleOutConnector.findAllSchedules();
    }

    public List<SchedulesModel> getAllDeletedSchedules() {
        return scheduleOutConnector.findAllByIsDeletedScheduled();
    }

    //회원별 일정목록
    @Transactional(readOnly = true)
    public Page<SchedulesModel> getSchedulesByUserFilter(String userId, Pageable pageable) {
        return scheduleOutConnector.findByUserId(userId,pageable);
    }

    //카테고리별 일정목록
    @Transactional(readOnly = true)
    public Page<SchedulesModel> getSchedulesByCategoryFilter(String categoryId,Pageable pageable) {
        return scheduleOutConnector.findByCategoryId(categoryId,pageable);
    }

    //일정상태별 일정목록
    @Transactional(readOnly = true)
    public Page<SchedulesModel> getSchedulesByStatus(String status,String userId,Pageable pageable) {
        return scheduleOutConnector.findAllByPROGRESS_STATUS(userId,status,pageable);
    }

    //오늘의 일정 조회
    @Transactional(readOnly = true)
    public List<SchedulesModel> findByTodaySchedule(Long userId){
        return scheduleOutConnector.findByTodaySchedule(userId);
    }

    //일정 단일 조회
    @Transactional(readOnly = true)
    public SchedulesModel findById(Long scheduleId) {
        return scheduleOutConnector.findById(scheduleId);
    }

    //일정 등록
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SchedulesModel saveSchedule(SchedulesModel model) {
        // 반복 없음이면 그냥 1건만 저장
        List<SchedulesModel> schedulesToSave = (model.getRepeatType() == RepeatType.NONE || model.getRepeatCount() == null || model.getRepeatCount() <= 0)
                        ? List.of(model)
                        : generateRepeatedSchedules(model);

        if (schedulesToSave.isEmpty()) {
            throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_CREATED_FAIL);
        }

        List<SchedulesModel> savedSchedules = new ArrayList<>();

        for (SchedulesModel m : schedulesToSave) {
            //일정 저장
            SchedulesModel saved = saveSingleSchedule(m,model,savedSchedules.isEmpty());
            savedSchedules.add(saved);
        }
        // 첫 번째 등록된 일정 반환
        SchedulesModel firstSchedule = savedSchedules.get(0);
        log.info("result:"+firstSchedule);
        //attachId 바인딩.
        if (model.getAttachIds() != null && !model.getAttachIds().isEmpty()) {
            attachInConnector.updateScheduleId(model.getAttachIds(), firstSchedule.getId());
            firstSchedule = firstSchedule.toBuilder()
                    .attachIds(model.getAttachIds())
                    .build();
        }
        log.info("일정 저장 완료, 이벤트 발행 시도");
        //리마인드 알림 디비 저장
        //notificationInConnector.createReminder(firstSchedule);
        // 이벤트 발행.
        publishScheduleEvent(firstSchedule,ScheduleActionType.SCHEDULE_CREATED);

        return firstSchedule;
    }

    //일정 수정
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SchedulesModel updateSchedule(Long scheduleId, SchedulesModel model,RepeatUpdateType updateType) {

        SchedulesModel existing = getValidatedUpdatableSchedule(scheduleId);
        log.info("수정 요청 들어옴. scheduleId = {}", scheduleId);
        updateType = Optional.ofNullable(updateType).orElse(RepeatUpdateType.SINGLE);
        log.info("Repeat Type?? = {}", updateType);
        switch (updateType) {
            //전부 수정
            case ALL -> {
                if (existing.getRepeatGroupId() == null || existing.getRepeatGroupId().isBlank()) {
                    throw new ScheduleCustomException(ScheduleErrorCode.INVALID_DELETE_TYPE_FOR_NON_REPEATED);
                }
                List<SchedulesModel> repeatSchedules = scheduleOutConnector.findByRepeatGroupId(existing.getRepeatGroupId());

                for (SchedulesModel target : repeatSchedules) {
                    SchedulesModel updated = buildUpdatedSchedule(target, model)
                            .toBuilder()
                            .scheduleType(classifySchedule(target))
                            .build();

                    handleAttachUpdate(target, updated);
                    updateProgressStatus(updated);
                    scheduleOutConnector.updateSchedule(target.getId(), updated);
                    publishScheduleEvent(updated, ScheduleActionType.SCHEDULE_UPDATE);
                }
                return scheduleOutConnector.findById(scheduleId); // 첫 일정 리턴
            }

            //일부 수정
            case AFTER_THIS -> {
                if (existing.getRepeatGroupId() == null || existing.getRepeatGroupId().isBlank()) {
                    throw new ScheduleCustomException(ScheduleErrorCode.INVALID_DELETE_TYPE_FOR_NON_REPEATED);
                }
                List<SchedulesModel> targets = scheduleOutConnector.findAfterStartTime(existing.getRepeatGroupId(), existing.getStartTime());

                for (SchedulesModel target : targets) {
                    SchedulesModel updated = buildUpdatedSchedule(target, model)
                            .toBuilder()
                            .scheduleType(classifySchedule(target))
                            .build();

                    handleAttachUpdate(target, updated);
                    updateProgressStatus(updated);
                    scheduleOutConnector.updateSchedule(target.getId(), updated);
                    publishScheduleEvent(updated, ScheduleActionType.SCHEDULE_UPDATE);
                }
                return scheduleOutConnector.findById(scheduleId);
            }

            //단일 수정
            case SINGLE -> {
                SchedulesModel updated = buildUpdatedSchedule(existing, model);
                updated = updated.toBuilder().scheduleType(classifySchedule(updated)).build();
                handleAttachUpdate(existing,updated);
                updateProgressStatus(updated);
                SchedulesModel result = scheduleOutConnector.updateSchedule(scheduleId, updated);
                publishScheduleEvent(result, ScheduleActionType.SCHEDULE_UPDATE);
                return result;
            }
            default -> throw new IllegalArgumentException("지원하지 않는 반복 수정 타입입니다.");
        }
    }

    public PROGRESS_STATUS updateProgressStatus(Long scheduleId, PROGRESS_STATUS newStatus) {
        scheduleOutConnector.updateStatusOnly(scheduleId, newStatus);
        return newStatus;
    }

    private SchedulesModel getValidatedUpdatableSchedule(Long scheduleId) {
        SchedulesModel schedule = scheduleOutConnector.findById(scheduleId);

        scheduleOutConnector.validateScheduleConflict(schedule);

        return schedule;
    }

    private void updateProgressStatus(SchedulesModel schedule) {
        schedule.updateProgressStatus();
    }
    
    //일정 삭제 (논리 삭제)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteSchedule(Long scheduleId, DeleteType deleteType) {
        SchedulesModel target = scheduleOutConnector.findById(scheduleId);

        switch (deleteType) {
            case SINGLE -> scheduleOutConnector.deleteSchedule(scheduleId);

            case AFTER_THIS -> {
                validateRepeatDelete(target);
                scheduleOutConnector.markAsDeletedAfter(target.getRepeatGroupId(),target.getStartTime());
            }

            case ALL_REPEAT -> {
                validateRepeatDelete(target);
                scheduleOutConnector.markAsDeletedByRepeatGroupId(target.getRepeatGroupId());
            }
        }
        //삭제후 이벤트 발행.
        publishScheduleEvent(target, ScheduleActionType.SCHEDULE_DELETE);

    }

    //선택 삭제
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteSchedules(List<Long> ids) {
        scheduleOutConnector.markAsDeletedByIds(ids);

        // 각 일정마다 이벤트 발행 (선택)
        for (Long id : ids) {
            SchedulesModel model = scheduleOutConnector.findById(id); // 이벤트 정보용
            publishScheduleEvent(model,ScheduleActionType.SCHEDULE_DELETE);
        }
    }

    //일괄삭제 기능 (자정마다 작동이 되게끔 하기)
    @Scheduled(cron = "0 0 0 * * ?")
    public void deleteOldSchedules() {
        LocalDateTime thresholdDate = LocalDateTime.now().minusMonths(1);
        scheduleOutConnector.deleteOldSchedules(thresholdDate);
    }

    private SchedulesModel buildUpdatedSchedule(SchedulesModel existing, SchedulesModel updates) {
        return existing.toBuilder()
                .contents(updates.getContents() != null ? updates.getContents() : existing.getContents())
                .scheduleDays(updates.getScheduleDays() != null ? updates.getScheduleDays() : existing.getScheduleDays())
                .scheduleMonth(updates.getScheduleMonth() != null ? updates.getScheduleMonth() : existing.getScheduleMonth())
                .startTime(updates.getStartTime() != null ? updates.getStartTime() : existing.getStartTime())
                .endTime(updates.getEndTime() != null ? updates.getEndTime() : existing.getEndTime())
                .categoryId(updates.getCategoryId() != null ? updates.getCategoryId() : existing.getCategoryId())
                .userId(updates.getUserId() != null ? updates.getUserId() : existing.getUserId())
                .repeatType(updates.getRepeatType() != null ? updates.getRepeatType() : existing.getRepeatType())
                .repeatCount(updates.getRepeatCount() != null ? updates.getRepeatCount() : existing.getRepeatCount())
                .repeatInterval(updates.getRepeatInterval() != null ? updates.getRepeatInterval() : existing.getRepeatInterval())
                .isAllDay(updates.isAllDay())
                .scheduleType(updates.getScheduleType() != null ? updates.getScheduleType() : existing.getScheduleType())
                .progressStatus(updates.getProgressStatus() != null ? updates.getProgressStatus() : existing.getProgressStatus())
                .attachIds(updates.getAttachIds() != null ? updates.getAttachIds() : existing.getAttachIds())
                .build();
    }

    private SchedulesModel saveSingleSchedule(SchedulesModel schedule, SchedulesModel originalModel, boolean isFirst) {

        ScheduleType type = classifySchedule(schedule);
        schedule = schedule.toBuilder().scheduleType(type).build();
        log.info(type.name());
        scheduleOutConnector.validateScheduleConflict(schedule);

        SchedulesModel saved = scheduleOutConnector.saveSchedule(schedule);

        if (saved == null || saved.getId() == null) {
            throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_CREATED_FAIL);
        }

        if (hasAttachFiles(originalModel) && isFirst) {
            attachInConnector.updateScheduleId(originalModel.getAttachIds(), saved.getId());
        }

        return saved;
    }

    private boolean hasAttachFiles(SchedulesModel model) {
        return model.getAttachIds() != null && !model.getAttachIds().isEmpty();
    }

    // 일정 수정용 첨부파일 업로드
    private void handleAttachUpdate(SchedulesModel existing, SchedulesModel updated) {
        try {
            List<Long> existingAttachIds = Optional.ofNullable(existing.getAttachIds()).orElse(new ArrayList<>());
            List<Long> newAttachIds = updated.getAttachIds(); // null 허용

            if (newAttachIds != null && !newAttachIds.isEmpty()) {
                List<Long> toDelete = new ArrayList<>(existingAttachIds);
                toDelete.removeAll(newAttachIds);

                for (Long attachId : toDelete) {
                    attachInConnector.deleteAttach(attachId);
                }

                attachInConnector.updateScheduleId(newAttachIds, existing.getId());
                updated = updated.toBuilder().attachIds(newAttachIds).build();
            }
        } catch (Exception e) {
            log.error("첨부파일 처리 중 오류 발생", e);
        }
    }


    //일정 반복기능
    private List<SchedulesModel> generateRepeatedSchedules(SchedulesModel baseModel) {
        log.info("반복일정 수행");
        List<SchedulesModel> result = new ArrayList<>();

        RepeatType rule = baseModel.getRepeatType();

        int count = Optional.ofNullable(baseModel.getRepeatCount()).orElse(1);
        int interval = Optional.ofNullable(baseModel.getRepeatInterval()).orElse(1);

        String groupId = UUID.randomUUID().toString();
        log.info(groupId);
        log.info(rule.name());
        switch (rule) {
            case DAILY, WEEKLY, MONTHLY -> {
                for (int i = 0; i < count; i++) {
                    SchedulesModel repeated = baseModel
                            .shiftScheduleBy(rule, i * interval)
                            .toBuilder()
                            .repeatType(rule)
                            .repeatCount(count)
                            .repeatInterval(interval)
                            .repeatGroupId(groupId)
                            .build();
                    result.add(repeated);
                }
            }
        }

        return result;
    }

    // 반복이 아닌 일정에 반복 삭제 요청 방어
    private void validateRepeatDelete(SchedulesModel model) {
        if (model.getRepeatGroupId() == null || model.getRepeatGroupId().isBlank()) {
            throw new ScheduleCustomException(ScheduleErrorCode.INVALID_DELETE_TYPE_FOR_NON_REPEATED);
        }
    }

    //일정 유형을 나누기.
    private ScheduleType classifySchedule(SchedulesModel model) {
        LocalDate start = model.getStartTime().toLocalDate();
        LocalDate end = model.getEndTime().toLocalDate();

        if (start.equals(end)) {
            log.info("단일과 하루종일");
            return model.isAllDay() ? ScheduleType.ALL_DAY : ScheduleType.SINGLE_DAY;
        } else {
            return ScheduleType.MULTI_DAY;
        }
    }
    //이벤트 발행
    private void publishScheduleEvent(SchedulesModel model, ScheduleActionType actionType) {
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
