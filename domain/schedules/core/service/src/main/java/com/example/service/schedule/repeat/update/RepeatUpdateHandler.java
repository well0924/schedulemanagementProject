package com.example.service.schedule.repeat.update;

import com.example.enumerate.schedules.RepeatUpdateType;
import com.example.model.schedules.SchedulesModel;

public interface RepeatUpdateHandler {
    // 일정 수정 타입
    RepeatUpdateType type();
    // 일정 수정 타입에 따른 로직처리
    SchedulesModel handle(SchedulesModel existing, SchedulesModel patch);

}
