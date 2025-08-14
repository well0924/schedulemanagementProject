package com.example.inbound.schedules;


import com.example.apimodel.attach.AttachApiModel;
import com.example.apimodel.schedule.ScheduleApiModel;
import com.example.enumerate.schedules.DeleteType;
import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.enumerate.schedules.RepeatUpdateType;
import com.example.inbound.attach.AttachInConnector;
import com.example.model.schedules.SchedulesModel;
import com.example.service.schedule.ScheduleDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleServiceConnectorImpl implements ScheduleServiceConnector {

    private final ScheduleDomainService scheduleDomainService;

    private final AttachInConnector attachInConnector;

    @Override
    public List<ScheduleApiModel.responseSchedule> findAllDeletedSchedules() {
        return scheduleDomainService.getAllDeletedSchedules()
                .stream()
                .map(this::toApiModelWithAttachments)
                .collect(Collectors.toList());
    }

    @Override
    public List<ScheduleApiModel.responseSchedule> findAll() {
        return scheduleDomainService
                .getAllSchedules()
                .stream()
                .map(this::toApiModelWithAttachments)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ScheduleApiModel.responseSchedule> getSchedulesByUserId(Pageable pageable) {
        return scheduleDomainService
                .getSchedulesByUserFilter(pageable)
                .map(this::toApiModelWithAttachments);
    }

    @Override
    public Page<ScheduleApiModel.responseSchedule> getSchedulesByCategoryName(String categoryName,Pageable pageable) {
        return scheduleDomainService
                .getSchedulesByCategoryFilter(categoryName,pageable)
                .map(this::toApiModelWithAttachments);
    }

    @Override
    public Page<ScheduleApiModel.responseSchedule> getSchedulesByStatus(String status, Pageable pageable) {
        return scheduleDomainService
                .getSchedulesByStatus(status,pageable)
                .map(this::toApiModelWithAttachments);
    }

    @Override
    public List<ScheduleApiModel.responseSchedule> findByTodaySchedule() {
        return scheduleDomainService.findByTodaySchedule().stream()
                .map(this::toApiModelWithAttachments)
                .collect(Collectors.toList());
    }

    @Override
    public ScheduleApiModel.responseSchedule findById(Long scheduleId) {
        SchedulesModel result = scheduleDomainService.findById(scheduleId);
        log.info("result"+result);
        return toApiModelWithAttachments(result);
    }

    @Override
    public ScheduleApiModel.responseSchedule saveSchedule(ScheduleApiModel.requestSchedule requestSchedule){
        SchedulesModel savedSchedule = scheduleDomainService.saveSchedule(toModel(requestSchedule));
        return toApiModelWithAttachments(savedSchedule);
    }

    @Override
    public ScheduleApiModel.responseSchedule updateSchedule(Long scheduleId, ScheduleApiModel.updateSchedule updateSchedule, RepeatUpdateType repeatUpdateType) {
        return toApiModelWithAttachments(scheduleDomainService.updateSchedule(scheduleId,toModel(updateSchedule),repeatUpdateType));
    }

    @Override
    public ScheduleApiModel.responseScheduleStatus updateScheduleStatus(Long scheduleId, PROGRESS_STATUS progressStatus) {
        PROGRESS_STATUS updated = scheduleDomainService.updateProgressStatus(scheduleId,progressStatus);
        return toApiScheduleProgressStatus(scheduleId, PROGRESS_STATUS.valueOf(updated.getValue()));
    }

    @Override
    public void deleteSchedule(Long scheduleId, DeleteType deleteType) {
        scheduleDomainService.deleteSchedule(scheduleId,deleteType);
    }

    @Override
    public void deleteOldSchedules() {
        scheduleDomainService.deleteOldSchedules();
    }

    @Override
    public void deleteSchedules(List<Long> ids) {
        scheduleDomainService.deleteSchedules(ids);
    }

    public SchedulesModel toModel(ScheduleApiModel.requestSchedule request) {
        return SchedulesModel.builder()
                .contents(request.contents())
                .scheduleDays(request.scheduleDays())
                .scheduleMonth(request.scheduleMonth())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .userId(request.userId())
                .categoryId(request.categoryId())
                .attachIds(request.attachIds())
                .progressStatus(PROGRESS_STATUS.IN_COMPLETE) // 기본값 설정
                .repeatType(request.repeatType())
                .repeatCount(request.repeatCount())
                .repeatInterval(request.repeatInterval())
                .isDeletedScheduled(false) // 기본값 설정
                .isAllDay(request.isAllDay())
                .scheduleType(request.scheduleType())
                .build();
    }

    //update 용
    public SchedulesModel toModel(ScheduleApiModel.updateSchedule request) {
        return SchedulesModel.builder()
                .contents(request.contents())
                .scheduleDays(request.scheduleDays())
                .scheduleMonth(request.scheduleMonth())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .categoryId(request.categoryId())
                .userId(request.userId())
                .progressStatus(PROGRESS_STATUS.IN_COMPLETE) // 기본값 설정
                .repeatType(request.repeatType())
                .repeatCount(request.repeatCount())
                .repeatInterval(request.repeatInterval())
                .isAllDay(request.isAllDay())
                .scheduleType(request.scheduleType())
                .build();
    }

    public ScheduleApiModel.responseSchedule toApiModelWithAttachments(SchedulesModel model) {
        List<AttachApiModel.AttachResponse> attachFiles;

        System.out.println(model.getAttachIds());
        attachFiles = model.getAttachIds() != null && !model.getAttachIds().isEmpty()
                ? attachInConnector.findByIds(model.getAttachIds())  // 첨부파일 정보 조회
                : Collections.emptyList();
        System.out.println("attachResult:::"+attachFiles);

        return ScheduleApiModel.responseSchedule
                .builder()
                .id(model.getId())
                .contents(model.getContents())
                .scheduleDays(model.getScheduleDays())
                .scheduleMonth(model.getScheduleMonth())
                .startTime(model.getStartTime())
                .endTime(model.getEndTime())
                .userId(model.getUserId())
                .categoryId(model.getCategoryId())
                .progressStatus(model.getProgressStatus())
                .repeatType(model.getRepeatType())
                .repeatCount(model.getRepeatCount())
                .repeatGroupId(model.getRepeatGroupId())
                .repeatInterval(model.getRepeatInterval())
                .isDeletedScheduled(model.isDeletedScheduled())
                .isAllDay(model.isAllDay())
                .scheduleType(model.getScheduleType())
                .createdBy(model.getCreatedBy())
                .createdTime(model.getCreatedTime())
                .updatedBy(model.getUpdatedBy())
                .updatedTime(model.getUpdatedTime())
                .attachFiles(attachFiles) // 첨부파일 포함
                .build();
    }

    private ScheduleApiModel.responseScheduleStatus toApiScheduleProgressStatus(Long scheduleId,PROGRESS_STATUS status) {
        return ScheduleApiModel.responseScheduleStatus
                .builder()
                .id(scheduleId)
                .progressStatus(status.getValue())
                .build();
    }
}
