package com.example.service.schedule.repeat.update;

import com.example.enumerate.schedules.RepeatUpdateType;
import com.example.events.enums.ScheduleActionType;
import com.example.model.schedules.SchedulesModel;
import com.example.outbound.schedule.ScheduleOutConnector;
import com.example.service.auth.SecurityUtil;
import com.example.service.schedule.guard.ScheduleGuard;
import com.example.service.schedule.support.AttachBinder;
import com.example.service.schedule.support.DomainEventPublisher;
import com.example.service.schedule.support.ScheduleClassifier;
import com.example.service.schedule.support.SchedulePatchApplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AllRepeatUpdateHandler implements RepeatUpdateHandler{

    private final ScheduleOutConnector out;
    private final ScheduleGuard guard;
    private final AttachBinder attachBinder;
    private final ScheduleClassifier classifier;
    private final DomainEventPublisher events;

    @Override
    public RepeatUpdateType type() {
        return RepeatUpdateType.AFTER_THIS;
    }

    @Override
    @Transactional
    public SchedulesModel handle(SchedulesModel existing, SchedulesModel patch) {
        guard.ensureRepeatable(existing);
        guard.assertOwnerOrAdmin(existing);

        Long me = SecurityUtil.currentUserId();
        boolean anyNotMine = out.findAfterStartTime(existing.getRepeatGroupId(), existing.getStartTime())
                .stream().anyMatch(s -> !me.equals(s.getUserId()));
        if (anyNotMine) throw guard.notOwner();

        out.findAfterStartTime(existing.getRepeatGroupId(), existing.getStartTime()).forEach(target -> {
            SchedulesModel updated = SchedulePatchApplier.apply(target, patch);
            updated = classifier.normalizeAndClassify(updated);
            updated = attachBinder.handleAttachUpdate(target, updated);
            updated.updateProgressStatus();
            out.updateSchedule(target.getId(), updated);
            events.publishScheduleEvent(updated, ScheduleActionType.SCHEDULE_UPDATE);
        });
        return null;
    }
}
