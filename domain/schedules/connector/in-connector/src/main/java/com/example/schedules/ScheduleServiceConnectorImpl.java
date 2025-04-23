package com.example.schedules;

import com.example.apimodel.ScheduleApiModel;
import com.example.apimodel.attach.AttachApiModel;
import com.example.enumerate.schedules.DeleteType;
import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.inconnector.attach.AttachInConnector;
import com.example.model.schedules.SchedulesModel;
import com.example.schedule.ScheduleDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    public Page<ScheduleApiModel.responseSchedule> getSchedulesByUserId(String userId, Pageable pageable) {
        return scheduleDomainService
                .getSchedulesByUserFilter(userId, pageable)
                .map(this::toApiModelWithAttachments);
    }

    @Override
    public Page<ScheduleApiModel.responseSchedule> getSchedulesByCategoryName(String categoryName,Pageable pageable) {
        return scheduleDomainService
                .getSchedulesByCategoryFilter(categoryName,pageable)
                .map(this::toApiModelWithAttachments);
    }

    @Override
    public Page<ScheduleApiModel.responseSchedule> getSchedulesByStatus(String status, String userId,Pageable pageable) {
        return scheduleDomainService
                .getSchedulesByStatus(status,userId,pageable)
                .map(this::toApiModelWithAttachments);
    }

    @Override
    public ScheduleApiModel.responseSchedule findById(Long scheduleId) {
        SchedulesModel result = scheduleDomainService.findById(scheduleId);
        log.info("result"+result);
        return toApiModelWithAttachments(result);
    }

    @Override
    public ScheduleApiModel.responseSchedule saveSchedule(ScheduleApiModel.requestSchedule requestSchedule) throws IOException {
        SchedulesModel savedSchedule = scheduleDomainService.saveSchedule(toModel(requestSchedule));
        return toApiModelWithAttachments(savedSchedule);
    }

    @Override
    public ScheduleApiModel.responseSchedule updateSchedule(Long scheduleId,ScheduleApiModel.updateSchedule updateSchedule, List<MultipartFile> files) throws IOException {
        return toApiModelWithAttachments(scheduleDomainService.updateSchedule(scheduleId,toModel(updateSchedule),files));
    }

    @Override
    public void deleteSchedule(Long scheduleId, DeleteType deleteType) {
        scheduleDomainService.deleteSchedule(scheduleId,deleteType);
    }

    @Override
    public void deleteOldSchedules() {
        scheduleDomainService.deleteOldSchedules();
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
                .createdBy(model.getCreatedBy())
                .createdTime(model.getCreatedTime())
                .updatedBy(model.getUpdatedBy())
                .updatedTime(model.getUpdatedTime())
                .attachFiles(attachFiles) // 첨부파일 포함
                .build();
    }

}
