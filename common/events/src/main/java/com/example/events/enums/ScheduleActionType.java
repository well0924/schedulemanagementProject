package com.example.events.enums;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public enum ScheduleActionType {
    SCHEDULE_CREATED,
    SCHEDULE_UPDATE,
    SCHEDULE_DELETE,
    SCHEDULE_REMINDER //리마인드 알림.
}
