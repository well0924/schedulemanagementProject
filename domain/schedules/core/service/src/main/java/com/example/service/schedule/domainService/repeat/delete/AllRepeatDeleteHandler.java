package com.example.service.schedule.domainService.repeat.delete;

import com.example.enumerate.schedules.DeleteType;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.model.schedules.SchedulesModel;
import com.example.security.config.SecurityUtil;
import com.example.service.schedule.domainService.guard.ScheduleGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AllRepeatDeleteHandler implements RepeatDeleteHandler {

    private final ScheduleRepositoryPort out;
    private final ScheduleGuard guard;

    @Override
    public DeleteType type() {
        return DeleteType.ALL_REPEAT;
    }

    @Override
    @Transactional
    public List<SchedulesModel> handle(SchedulesModel target) {
        guard.validateRepeatDelete(target);

        List<SchedulesModel> targets = out.findAfterStartTime(target.getRepeatGroupId(), target.getStartTime());

        Long me = SecurityUtil.currentUserId();
        if (targets.stream().anyMatch(s -> !me.equals(s.getMemberId()))) throw guard.notOwner();

        out.markAsDeletedByRepeatGroupId(target.getRepeatGroupId());

        return targets;
    }
}
