package com.example.events;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public enum ScheduleActionType {
    SCHEDULE_CREATED,
    SCHEDULE_UPDATED,
    SCHEDULE_DELETED
}
