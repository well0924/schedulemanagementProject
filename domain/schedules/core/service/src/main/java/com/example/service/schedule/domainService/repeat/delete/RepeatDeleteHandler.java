package com.example.service.schedule.domainService.repeat.delete;

import com.example.enumerate.schedules.DeleteType;
import com.example.model.schedules.SchedulesModel;

import java.util.List;

public interface RepeatDeleteHandler {

    DeleteType type();

    List<SchedulesModel> handle(SchedulesModel target);

}
