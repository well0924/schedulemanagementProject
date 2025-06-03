package com.example.events.enums;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public enum ScheduleActionType {
    SCHEDULE_CREATED,
    SCHEDULE_UPDATE,
    SCHEDULE_DELETE
}
