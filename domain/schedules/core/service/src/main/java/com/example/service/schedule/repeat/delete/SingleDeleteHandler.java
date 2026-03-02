package com.example.service.schedule.repeat.delete;

import com.example.enumerate.schedules.DeleteType;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.model.schedules.SchedulesModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SingleDeleteHandler implements RepeatDeleteHandler{

    private final ScheduleRepositoryPort repositoryPort;

    @Override
    public DeleteType type() {
        return DeleteType.SINGLE;
    }

    @Transactional
    @Override
    public List<SchedulesModel> handle(SchedulesModel target) {
        log.info("single-delete");
        repositoryPort.deleteSchedule(target.getId());
        return List.of(target);
    }
}
