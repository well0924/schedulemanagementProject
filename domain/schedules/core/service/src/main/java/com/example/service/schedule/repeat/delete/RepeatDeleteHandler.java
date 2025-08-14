package com.example.service.schedule.repeat.delete;

import com.example.enumerate.schedules.DeleteType;
import com.example.model.schedules.SchedulesModel;

public interface RepeatDeleteHandler {

    DeleteType type();

    void handle(SchedulesModel target);

}
