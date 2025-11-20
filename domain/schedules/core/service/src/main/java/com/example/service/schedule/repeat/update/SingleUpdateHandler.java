package com.example.service.schedule.repeat.update;

import com.example.enumerate.schedules.RepeatUpdateType;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.model.schedules.SchedulesModel;
import com.example.service.schedule.support.AttachBinder;
import com.example.service.schedule.support.ScheduleClassifier;
import com.example.service.schedule.support.SchedulePatchApplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SingleUpdateHandler implements RepeatUpdateHandler {

    private final ScheduleClassifier scheduleClassifier;
    private final AttachBinder attachBinder;
    private final ScheduleRepositoryPort scheduleOutConnector;

    @Override
    public RepeatUpdateType type() {
        return RepeatUpdateType.SINGLE;
    }

    @Override
    @Transactional
    public List<SchedulesModel> handle(SchedulesModel existing, SchedulesModel patch) {
        SchedulesModel updated = SchedulePatchApplier.apply(existing,patch);
        updated = updated.toBuilder().scheduleType(scheduleClassifier.classify(updated)).build();
        updated = attachBinder.handleAttachUpdate(existing,updated);
        updated.updateProgressStatus();
        SchedulesModel result = scheduleOutConnector.updateSchedule(existing.getId(), updated);
        return List.of(result);
    }

}
