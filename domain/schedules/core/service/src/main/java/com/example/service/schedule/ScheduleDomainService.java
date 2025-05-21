package com.example.service.schedule;

import com.example.enumerate.schedules.DeleteType;
import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.enumerate.schedules.RepeatType;
import com.example.enumerate.schedules.ScheduleType;
import com.example.events.NotificationChannel;
import com.example.events.NotificationEvents;
import com.example.events.ScheduleActionType;
import com.example.events.ScheduleEvents;
import com.example.events.outbox.OutboxEventService;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import com.example.inbound.attach.AttachInConnector;
import com.example.model.schedules.SchedulesModel;
import com.example.outbound.schedule.ScheduleOutConnector;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class ScheduleDomainService {

    private final ScheduleOutConnector scheduleOutConnector;

    private final AttachInConnector attachInConnector;

    private final OutboxEventService outboxEventService;

    public List<SchedulesModel> getAllSchedules() {
        return scheduleOutConnector.findAllSchedules();
    }

    public List<SchedulesModel> getAllDeletedSchedules() {
        return scheduleOutConnector.findAllByIsDeletedScheduled();
    }

    //회원별 일정목록
    public Page<SchedulesModel> getSchedulesByUserFilter(String userId, Pageable pageable) {
        return scheduleOutConnector.findByUserId(userId,pageable);
    }

    //카테고리별 일정목록
    public Page<SchedulesModel> getSchedulesByCategoryFilter(String categoryId,Pageable pageable) {
        return scheduleOutConnector.findByCategoryId(categoryId,pageable);
    }

    //일정상태별 일정목록
    public Page<SchedulesModel> getSchedulesByStatus(String status,String userId,Pageable pageable) {
        return scheduleOutConnector.findAllByPROGRESS_STATUS(userId,status,pageable);
    }

    //오늘의 일정 조회
    public List<SchedulesModel> findByTodaySchedule(Long userId){
        return scheduleOutConnector.findByTodaySchedule(userId);
    }

    //일정 단일 조회
    public SchedulesModel findById(Long scheduleId) {
        return scheduleOutConnector.findById(scheduleId);
    }

    //일정 등록
    public SchedulesModel saveSchedule(SchedulesModel model) {
        List<SchedulesModel> schedulesToSave;

        // 반복 없음이면 그냥 1건만 저장
        if (model.getRepeatType() == RepeatType.NONE || model.getRepeatCount() == null || model.getRepeatCount() <= 0) {
            schedulesToSave = List.of(model);
        } else {
            schedulesToSave = generateRepeatedSchedules(model);
        }

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
        // 이벤트 발행.
        NotificationEvents notificationEvents = NotificationEvents
                .builder()
                .receiverId(firstSchedule.getUserId())
                .message("📅 일정이 생성되었습니다: " + firstSchedule.getContents())
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .notificationChannel(NotificationChannel.WEB)
                .createdTime(LocalDateTime.now())
                .build();

        outboxEventService.saveEvent(notificationEvents,
                "SCHEDULE",
                String.valueOf(notificationEvents.getReceiverId()),
                ScheduleActionType.SCHEDULE_CREATED.name());
        return firstSchedule;
    }

    //일정 수정
    public SchedulesModel updateSchedule(Long scheduleId, SchedulesModel model) {

        SchedulesModel existing = getValidatedUpdatableSchedule(scheduleId);
        // 변경될 모델 구성
        SchedulesModel updated = buildUpdatedSchedule(existing,model);

        // scheduleType 재분류
        ScheduleType type = classifySchedule(updated);
        updated = updated.toBuilder().scheduleType(type).build();

        try {
            List<Long> existingAttachIds = Optional.ofNullable(existing.getAttachIds()).orElse(new ArrayList<>());
            List<Long> newAttachIds = model.getAttachIds(); // null 허용

            if (newAttachIds != null && !newAttachIds.isEmpty()) {
                // 일부 또는 전체 교체 요청
                List<Long> toDelete = new ArrayList<>(existingAttachIds);
                toDelete.removeAll(newAttachIds);

                for (Long attachId : toDelete) {
                    attachInConnector.deleteAttach(attachId);
                }

                attachInConnector.updateScheduleId(newAttachIds, scheduleId);
                updated = updated.toBuilder().attachIds(newAttachIds).build();
            }
            // 3. attachIds == null → 아무 것도 하지 않음 → 기존 그대로 유지

        } catch (Exception e) {
            log.error("첨부파일 처리 중 오류 발생", e);
        }
        //일정상태 수정
        updateProgressStatus(updated);
        //일정 수정 로직
        SchedulesModel result = scheduleOutConnector.updateSchedule(scheduleId, updated);

        // 일정 수정 후 이벤트 발행
        ScheduleEvents scheduleEvents = new ScheduleEvents(
                result.getId(),
                result.getUserId(),
                result.getContents(),
                ScheduleActionType.SCHEDULE_UPDATED,
                NotificationChannel.WEB,
                LocalDateTime.now()
        );
        return result;
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
        ScheduleEvents scheduleEvents = new ScheduleEvents(
                target.getId(),
                target.getUserId(),
                target.getContents(),
                ScheduleActionType.SCHEDULE_DELETED,
                NotificationChannel.WEB,
                LocalDateTime.now()
        );
        //applicationEventPublisher.publishEvent(scheduleEvents);
    }

    //선택 삭제
    public void deleteSchedules(List<Long> ids) {
        scheduleOutConnector.markAsDeletedByIds(ids);

        // 각 일정마다 이벤트 발행 (선택)
        for (Long id : ids) {
            SchedulesModel model = scheduleOutConnector.findById(id); // 이벤트 정보용
            ScheduleEvents scheduleEvents = new ScheduleEvents(
                    model.getId(),
                    model.getUserId(),
                    model.getContents(),
                    ScheduleActionType.SCHEDULE_DELETED,
                    NotificationChannel.WEB,
                    LocalDateTime.now()
            );

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

    //일정 반복기능
    private List<SchedulesModel> generateRepeatedSchedules(SchedulesModel baseModel) {
        List<SchedulesModel> result = new ArrayList<>();

        RepeatType rule = baseModel.getRepeatType();

        int count = Optional.ofNullable(baseModel.getRepeatCount()).orElse(1);
        int interval = Optional.ofNullable(baseModel.getRepeatInterval()).orElse(1);

        String groupId = UUID.randomUUID().toString();

        for (int i = 0; i < count; i++) {
            if (rule == RepeatType.NONE && i > 0) continue;

            SchedulesModel repeated = baseModel
                    .shiftScheduleBy(rule, i*interval)
                    .toBuilder()
                    .repeatType(baseModel.getRepeatType())   // 반복 일정은 반복 없음으로 저장
                    .repeatCount(baseModel.getRepeatCount())
                    .repeatInterval(baseModel.getRepeatInterval()) //반복 간격.
                    .repeatGroupId(groupId) // 반복일정의 groupId
                    .build();

            log.debug("groupId::"+groupId);
            log.debug("repeated::"+repeated);
            result.add(repeated);
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
}
