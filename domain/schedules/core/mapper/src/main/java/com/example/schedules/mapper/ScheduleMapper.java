package com.example.schedules.mapper;

import com.example.apimodel.attach.AttachApiModel;
import com.example.apimodel.schedule.ScheduleApiModel;
import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.model.schedules.SchedulesModel;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class ScheduleMapper {

    private static final long DEFAULT_ID = -1L;

    public SchedulesModel toModel(ScheduleApiModel.requestSchedule req) {
        return SchedulesModel.builder()
                .contents(req.contents())
                .scheduleDays(req.scheduleDays())
                .scheduleMonth(req.scheduleMonth())
                .startTime(req.startTime())
                .endTime(req.endTime())
                .memberId(req.userId())
                .categoryId(req.categoryId())
                .repeatType(req.repeatType())
                .repeatCount(req.repeatCount())
                .repeatInterval(req.repeatInterval())
                .isAllDay(req.isAllDay())
                .scheduleType(req.scheduleType())
                .progressStatus(PROGRESS_STATUS.IN_COMPLETE)
                .attachIds(req.attachIds())
                .build();
    }

    public SchedulesModel toModel(ScheduleApiModel.updateSchedule req) {
        return SchedulesModel.builder()
                .contents(req.contents())
                .scheduleDays(req.scheduleDays())
                .scheduleMonth(req.scheduleMonth())
                .startTime(req.startTime())
                .endTime(req.endTime())
                .categoryId(req.categoryId())
                .memberId(req.memberId())
                .repeatType(req.repeatType())
                .repeatCount(req.repeatCount())
                .repeatInterval(req.repeatInterval())
                .isAllDay(req.isAllDay())
                .scheduleType(req.scheduleType())
                .progressStatus(PROGRESS_STATUS.IN_COMPLETE)
                .build();
    }

    public ScheduleApiModel.responseSchedule toApi(SchedulesModel schedulesModel){
        return ScheduleApiModel.responseSchedule
                .builder()
                .id(Optional.ofNullable(schedulesModel.getId()).orElse(DEFAULT_ID))
                .contents(schedulesModel.getContents())
                .categoryId(Optional.ofNullable(schedulesModel.getCategoryId()).orElse(DEFAULT_ID))
                .memberId(schedulesModel.getMemberId())
                .scheduleMonth(schedulesModel.getScheduleMonth())
                .scheduleDays(schedulesModel.getScheduleDays())
                .startTime(schedulesModel.getStartTime())
                .endTime(schedulesModel.getEndTime())
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();
    }

    public ScheduleApiModel.responseSchedule toResponse(SchedulesModel model, List<AttachApiModel.AttachResponse> attachFiles) {
        return ScheduleApiModel.responseSchedule.builder()
                .id(model.getId())
                .contents(model.getContents())
                .scheduleDays(model.getScheduleDays())
                .scheduleMonth(model.getScheduleMonth())
                .startTime(model.getStartTime())
                .endTime(model.getEndTime())
                .memberId(model.getMemberId())
                .categoryId(model.getCategoryId())
                .progressStatus(model.getProgressStatus())
                .repeatType(model.getRepeatType())
                .repeatCount(model.getRepeatCount())
                .repeatInterval(model.getRepeatInterval())
                .repeatGroupId(model.getRepeatGroupId())
                .isAllDay(model.isAllDay())
                .scheduleType(model.getScheduleType())
                .createdBy(model.getCreatedBy())
                .createdTime(model.getCreatedTime())
                .updatedBy(model.getUpdatedBy())
                .updatedTime(model.getUpdatedTime())
                .attachFiles(attachFiles != null ? attachFiles : Collections.emptyList())
                .build();
    }

}
