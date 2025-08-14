package com.example.service.schedule.support;

import com.example.model.schedules.SchedulesModel;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class SchedulePatchApplier {

    public static SchedulesModel apply(SchedulesModel existing, SchedulesModel updates) {
        return existing.toBuilder()
                .contents(nvl(updates.getContents(), existing.getContents()))
                .scheduleDays(nvl(updates.getScheduleDays(), existing.getScheduleDays()))
                .scheduleMonth(nvl(updates.getScheduleMonth(), existing.getScheduleMonth()))
                .startTime(nvl(updates.getStartTime(), existing.getStartTime()))
                .endTime(nvl(updates.getEndTime(), existing.getEndTime()))
                .categoryId(nvl(updates.getCategoryId(), existing.getCategoryId()))
                .userId(existing.getUserId()) // 소유자 불변
                .repeatType(nvl(updates.getRepeatType(), existing.getRepeatType()))
                .repeatCount(nvl(updates.getRepeatCount(), existing.getRepeatCount()))
                .repeatInterval(nvl(updates.getRepeatInterval(), existing.getRepeatInterval()))
                .isAllDay(updates.isAllDay())
                .scheduleType(nvl(updates.getScheduleType(), existing.getScheduleType()))
                .progressStatus(nvl(updates.getProgressStatus(), existing.getProgressStatus()))
                .attachIds(nvl(updates.getAttachIds(), existing.getAttachIds()))
                .build();
    }

    private static <T> T nvl(T v, T def){ return v != null ? v : def; }
}
