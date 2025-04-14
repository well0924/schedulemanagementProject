package com.example.schedule;

import com.example.enumerate.schedules.DeleteType;
import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.enumerate.schedules.RepeatType;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import com.example.inconnector.attach.AttachInConnector;
import com.example.model.schedules.SchedulesModel;
import com.example.schedule.eventcommand.ScheduleEvents;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional(readOnly = true)
    public List<SchedulesModel> getAllSchedules() {
        return scheduleOutConnector.findAllSchedules();
    }

    @Transactional(readOnly = true)
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

    //일정 단일 조회
    @Transactional(readOnly = true)
    public SchedulesModel findById(Long scheduleId) {
        return scheduleOutConnector.findById(scheduleId);
    }

    //일정 등록
    public SchedulesModel saveSchedule(SchedulesModel model) {
        // 반복 일정 생성
        List<SchedulesModel> schedulesToSave = generateRepeatedSchedules(model);
        List<SchedulesModel> savedSchedules = new ArrayList<>();

        for (SchedulesModel m : schedulesToSave) {
            //일정 저장
            SchedulesModel saved = saveSingleSchedule(m,model,savedSchedules.isEmpty());
            savedSchedules.add(saved);
        }
        // 첫 번째 등록된 일정 반환
        SchedulesModel firstSchedule = savedSchedules.get(0);
        // 이벤트 발행.
        applicationEventPublisher.publishEvent(
                new ScheduleEvents(
                        firstSchedule.getId(),
                        firstSchedule.getUserId(),
                        "CREATE",
                        firstSchedule.getContents(),
                        LocalDateTime.now()
                )
        );
        return firstSchedule;
    }

    //일정 수정
    public SchedulesModel updateSchedule(Long scheduleId, SchedulesModel model, List<MultipartFile> files) throws IOException {

        SchedulesModel schedule = getValidatedUpdatableSchedule(scheduleId);
        // 변경될 모델 구성
        SchedulesModel updated = buildUpdatedSchedule(schedule,model);

        updateScheduleStatus(updated);

        updateAttachmentsIfExists(files, scheduleId);

        SchedulesModel result = scheduleOutConnector.updateSchedule(scheduleId, schedule);

        // 일정 수정 후 이벤트 발행
        applicationEventPublisher.publishEvent(
                new ScheduleEvents(
                        result.getId(),
                        result.getUserId(),
                        "UPDATE",
                        result.getContents(),
                        LocalDateTime.now()
                )
        );

        return result;
    }

    private SchedulesModel getValidatedUpdatableSchedule(Long scheduleId) {
        SchedulesModel schedule = scheduleOutConnector.findById(scheduleId);

        scheduleOutConnector.validateScheduleConflict(schedule);

        if (schedule.getProgressStatus() == PROGRESS_STATUS.COMPLETE) {
            throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_COMPLETED);
        }
        return schedule;
    }

    private void updateScheduleStatus(SchedulesModel schedule) {
        schedule.updateSchedule(schedule.getStartTime(), schedule.getEndTime());
        if (schedule.getProgressStatus() == PROGRESS_STATUS.COMPLETE) {
            schedule.markAsComplete();
        }
    }

    private void updateAttachmentsIfExists(List<MultipartFile> files, Long scheduleId) throws IOException {
        if (files != null && !files.isEmpty()) {
            attachInConnector.updateAttach(files, scheduleId);
        }
    }

    //일정 삭제 (논리 삭제)
    public void deleteSchedule(Long scheduleId, DeleteType deleteType) {
        SchedulesModel target = scheduleOutConnector.findById(scheduleId);

        switch (deleteType) {
            case SINGLE -> scheduleOutConnector.deleteSchedule(scheduleId);

            case AFTER_THIS -> scheduleOutConnector.markAsDeletedAfter(target.getRepeatGroupId(),target.getStartTime());

            case ALL_REPEAT -> scheduleOutConnector.markAsDeletedByRepeatGroupId(target.getRepeatGroupId());
        }
        //삭제후 이벤트 발행.
        applicationEventPublisher.publishEvent(
                new ScheduleEvents(
                        target.getId(),
                        target.getUserId(),
                        "UPDATE",
                        target.getContents(),
                        LocalDateTime.now()
                )
        );
    }

    //일괄삭제 기능 (자정마다 작동이 되게끔 하기)
    @Scheduled(cron = "0 0 0 * * ?")
    public void deleteOldSchedules() {
        LocalDateTime thresholdDate = LocalDateTime.now().minusMonths(1);
        scheduleOutConnector.deleteOldSchedules(thresholdDate);
    }

    private SchedulesModel buildUpdatedSchedule(SchedulesModel existing, SchedulesModel updates) {
        return existing.toBuilder()
                .contents(updates.getContents())
                .scheduleDays(updates.getScheduleDays())
                .scheduleMonth(updates.getScheduleMonth())
                .startTime(updates.getStartTime())
                .endTime(updates.getEndTime())
                .categoryId(updates.getCategoryId())
                .userId(updates.getUserId())
                .repeatType(updates.getRepeatType())
                .repeatCount(updates.getRepeatCount())
                .build();
    }

    private SchedulesModel saveSingleSchedule(SchedulesModel schedule, SchedulesModel originalModel, boolean isFirst) {
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

}
