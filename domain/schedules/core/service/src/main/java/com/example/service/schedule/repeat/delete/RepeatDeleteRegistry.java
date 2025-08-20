package com.example.service.schedule.repeat.delete;

import com.example.enumerate.schedules.DeleteType;
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
public class RepeatDeleteRegistry {

    private final List<RepeatDeleteHandler> handlers;
    private final Map<DeleteType, RepeatDeleteHandler> map = new EnumMap<>(DeleteType.class);

    @PostConstruct
    void init() { handlers.forEach(h -> map.put(h.type(), h)); }

    public void dispatch(DeleteType type, SchedulesModel target) {
        RepeatDeleteHandler h = map.get(type);
        if (h == null) throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_DELETE_FAIL, "No handler for " + type);
        h.handle(target);
    }
}
