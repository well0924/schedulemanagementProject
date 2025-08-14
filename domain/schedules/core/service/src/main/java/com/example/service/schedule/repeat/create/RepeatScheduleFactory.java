package com.example.service.schedule.repeat.create;

import com.example.enumerate.schedules.RepeatType;
import com.example.model.schedules.SchedulesModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class RepeatScheduleFactory {

    public List<SchedulesModel> generateRepeatedSchedules(SchedulesModel baseModel) {
        log.info("반복일정 수행");
        List<SchedulesModel> result = new ArrayList<>();

        RepeatType rule = baseModel.getRepeatType();

        int count = Optional.ofNullable(baseModel.getRepeatCount()).orElse(1);
        int interval = Optional.ofNullable(baseModel.getRepeatInterval()).orElse(1);

        String groupId = UUID.randomUUID().toString();
        log.info(groupId);
        log.info(rule.name());
        switch (rule) {
            case DAILY, WEEKLY, MONTHLY -> {
                for (int i = 0; i < count; i++) {
                    SchedulesModel repeated = baseModel
                            .shiftScheduleBy(rule, i * interval)
                            .toBuilder()
                            .repeatType(rule)
                            .repeatCount(count)
                            .repeatInterval(interval)
                            .repeatGroupId(groupId)
                            .build();
                    result.add(repeated);
                }
            }
        }
        return result;
    }

}
