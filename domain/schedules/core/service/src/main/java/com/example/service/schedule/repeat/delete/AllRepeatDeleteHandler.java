package com.example.service.schedule.repeat.delete;

import com.example.enumerate.schedules.DeleteType;
import com.example.model.schedules.SchedulesModel;
import com.example.outbound.schedule.ScheduleOutConnector;
import com.example.service.auth.SecurityUtil;
import com.example.service.schedule.guard.ScheduleGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AllRepeatDeleteHandler implements RepeatDeleteHandler {

    private final ScheduleOutConnector out;
    private final ScheduleGuard guard;

    @Override
    public DeleteType type() {
        return DeleteType.ALL_REPEAT;
    }

    @Override
    @Transactional
    public void handle(SchedulesModel target) {
        guard.validateRepeatDelete(target);
        Long me = SecurityUtil.currentUserId();
        boolean anyNotMine = out.findAfterStartTime(target.getRepeatGroupId(), target.getStartTime())
                .stream().anyMatch(t -> !me.equals(t.getMemberId()));
        if (anyNotMine) throw guard.notOwner();
        out.markAsDeletedByRepeatGroupId(target.getRepeatGroupId());
    }
}
