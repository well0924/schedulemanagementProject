package com.example.schedules.mapper;

import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.enumerate.schedules.RepeatType;
import com.example.enumerate.schedules.ScheduleType;
import com.example.model.schedules.SchedulesModel;
import com.example.rdbrepository.Schedules;
import org.springframework.stereotype.Component;

@Component
public class ScheduleEntityMapper {

    public Schedules toEntity(SchedulesModel model) {
        return Schedules
                .builder()
                .id(model.getId())
                .contents(model.getContents())
                .scheduleDay(model.getScheduleDays())
                .scheduleMonth(model.getScheduleMonth())
                .startTime(model.getStartTime())
                .endTime(model.getEndTime())
                .memberId(model.getMemberId())
                .categoryId(model.getCategoryId())
                .isDeletedScheduled(model.isDeletedScheduled())
                .progress_status(String.valueOf(model.getProgressStatus()))
                .repeatType(String.valueOf(model.getRepeatType()))
                .repeatCount(model.getRepeatCount())
                .repeatInterval(model.getRepeatInterval())
                .repeatGroupId(model.getRepeatGroupId())
                .isAllDay(model.isAllDay())
                .scheduleType(String.valueOf(model.getScheduleType()))
                .build();
    }

    public SchedulesModel toModel(Schedules entity) {
        return SchedulesModel.builder()
                .id(entity.getId())
                .contents(entity.getContents())
                .scheduleDays(entity.getScheduleDay())
                .scheduleMonth(entity.getScheduleMonth())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .memberId(entity.getMemberId())
                .categoryId(entity.getCategoryId())
                .progressStatus(PROGRESS_STATUS.valueOf(entity.getProgress_status()))
                .repeatType(RepeatType.valueOf(entity.getRepeatType()))
                .repeatCount(entity.getRepeatCount())
                .repeatInterval(entity.getRepeatInterval())
                .repeatGroupId(entity.getRepeatGroupId())
                .isAllDay(entity.getIsAllDay())
                .scheduleType(ScheduleType.valueOf(entity.getScheduleType()))
                .createdBy(entity.getCreatedBy())
                .createdTime(entity.getCreatedTime())
                .updatedBy(entity.getUpdatedBy())
                .updatedTime(entity.getUpdatedTime())
                .build();
    }
}
