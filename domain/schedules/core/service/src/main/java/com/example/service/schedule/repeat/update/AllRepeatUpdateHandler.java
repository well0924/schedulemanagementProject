package com.example.service.schedule.repeat.update;

import com.example.enumerate.schedules.RepeatUpdateType;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.model.schedules.SchedulesModel;
import com.example.security.config.SecurityUtil;
import com.example.service.schedule.guard.ScheduleGuard;
import com.example.service.schedule.support.AttachBinder;
import com.example.service.schedule.support.ScheduleClassifier;
import com.example.service.schedule.support.SchedulePatchApplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

        Long me = SecurityUtil.currentUserId();
        boolean anyNotMine = out.findAfterStartTime(existing.getRepeatGroupId(), existing.getStartTime())
                .stream().anyMatch(s -> !me.equals(s.getMemberId()));
        if (anyNotMine) throw guard.notOwner();

        List<SchedulesModel> results = new ArrayList<>();
        out.findAfterStartTime(existing.getRepeatGroupId(), existing.getStartTime()).forEach(target -> {
            SchedulesModel updated = SchedulePatchApplier.apply(target, patch);
            updated = classifier.normalizeAndClassify(updated);
            updated = attachBinder.handleAttachUpdate(target, updated);
            updated.updateProgressStatus();
            SchedulesModel saved = out.updateSchedule(target.getId(), updated);
            results.add(saved);
        });
        return results;
    }
}
