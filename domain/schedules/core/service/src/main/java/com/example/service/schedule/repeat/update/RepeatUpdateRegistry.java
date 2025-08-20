package com.example.service.schedule.repeat.update;

import com.example.enumerate.schedules.RepeatUpdateType;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import com.example.model.schedules.SchedulesModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RepeatUpdateRegistry {

    private final List<RepeatUpdateHandler> handlers;
    private final Map<RepeatUpdateType, RepeatUpdateHandler> map = new EnumMap<>(RepeatUpdateType.class);

    @PostConstruct
    void init() {
        for (RepeatUpdateHandler h : handlers) {
            map.put(h.type(), h);
        }
    }

    public SchedulesModel dispatch(RepeatUpdateType type, SchedulesModel existing, SchedulesModel patch) {
        RepeatUpdateHandler h = map.get(type);
        if (h == null) throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_UPDATED_FAIL, "No handler for " + type);
        return h.handle(existing, patch);
    }
}
