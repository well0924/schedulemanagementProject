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
public class AfterThisDeleteHandler implements RepeatDeleteHandler {

    private final ScheduleGuard guard;
    private final ScheduleOutConnector out;


    @Override
    public DeleteType type() {
        return DeleteType.AFTER_THIS;
    }

    @Override
    @Transactional
    public void handle(SchedulesModel target) {
        guard.validateRepeatDelete(target);
        // 오너 검증: 이후 전부 내 거인지
        Long me = SecurityUtil.currentUserId();
        boolean anyNotMine = out.findAfterStartTime(target.getRepeatGroupId(), target.getStartTime())
                .stream().anyMatch(t -> !me.equals(t.getUserId()));
        if (anyNotMine) throw guard.notOwner();

        out.markAsDeletedAfter(target.getRepeatGroupId(), target.getStartTime());
    }
}
