package com.example.service.schedule.repeat.update;

import com.example.enumerate.schedules.RepeatUpdateType;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.model.schedules.SchedulesModel;
import com.example.security.config.SecurityUtil;
import com.example.service.schedule.guard.ScheduleGuard;
import com.example.service.schedule.support.AttachBinder;
import com.example.service.schedule.support.ScheduleClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AllRepeatUpdateHandler implements RepeatUpdateHandler{

    private final ScheduleRepositoryPort out;
    private final ScheduleGuard guard;
    private final AttachBinder attachBinder;
    private final ScheduleClassifier classifier;

    @Override
    public RepeatUpdateType type() {
        return RepeatUpdateType.ALL;
    }

    @Override
    @Transactional
    public List<SchedulesModel> handle(SchedulesModel existing, SchedulesModel patch) {
        guard.ensureRepeatable(existing);
        guard.assertOwnerOrAdmin(existing);

        List<SchedulesModel> targets = out.findAfterStartTime(existing.getRepeatGroupId(), existing.getStartTime());

        Long me = SecurityUtil.currentUserId();
        if (targets.stream().anyMatch(s -> !me.equals(s.getMemberId()))) throw guard.notOwner();

        List<SchedulesModel> results = transformTargets(targets,patch,classifier,attachBinder);
        return out.saveAll(results);
    }
}
