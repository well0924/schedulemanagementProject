package com.example.inbound.schedules;


import com.example.apimodel.attach.AttachApiModel;
import com.example.apimodel.schedule.ScheduleApiModel;
import com.example.enumerate.schedules.DeleteType;
import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.enumerate.schedules.RepeatUpdateType;
import com.example.inbound.attach.AttachInConnector;
import com.example.model.schedules.SchedulesModel;
import com.example.schedules.mapper.ScheduleMapper;
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

    private final ScheduleMapper scheduleMapper;

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
        SchedulesModel savedSchedule = scheduleDomainService.saveSchedule(scheduleMapper.toModel(requestSchedule));
        return toApiModelWithAttachments(savedSchedule);
    }

    @Override
    public ScheduleApiModel.responseSchedule updateSchedule(Long scheduleId, ScheduleApiModel.updateSchedule updateSchedule, RepeatUpdateType repeatUpdateType) {
        return toApiModelWithAttachments(scheduleDomainService.updateSchedule(scheduleId,scheduleMapper.toModel(updateSchedule),repeatUpdateType));
    }

    @Override
    public ScheduleApiModel.responseScheduleStatus updateScheduleStatus(Long scheduleId, PROGRESS_STATUS progressStatus) {
        PROGRESS_STATUS updated = scheduleDomainService.updateProgressStatus(scheduleId,progressStatus);
        return ScheduleApiModel.responseScheduleStatus
                .builder()
                .id(scheduleId)
                .progressStatus(updated.getValue())
                .build();
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

    public ScheduleApiModel.responseSchedule toApiModelWithAttachments(SchedulesModel model) {
        List<AttachApiModel.AttachResponse> attachFiles;

        attachFiles = model.getAttachIds() != null && !model.getAttachIds().isEmpty()
                ? attachInConnector.findByIds(model.getAttachIds())  // 첨부파일 정보 조회
                : Collections.emptyList();

        return scheduleMapper.toResponse(model,attachFiles);
    }
}
