package com.example.events.spring;

import com.example.events.enums.ScheduleActionType;
import com.example.model.schedules.SchedulesModel;

import java.util.List;

public record ScheduleDomainEvent(List<SchedulesModel> schedules, ScheduleActionType actionType) {
}
