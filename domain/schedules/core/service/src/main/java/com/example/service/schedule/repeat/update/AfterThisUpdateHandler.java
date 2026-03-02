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
public class AfterThisUpdateHandler implements RepeatUpdateHandler {

    private final ScheduleRepositoryPort out;
    private final ScheduleGuard guard;
    private final AttachBinder attachBinder;
    private final ScheduleClassifier classifier;

    @Override
    public RepeatUpdateType type() {
        return RepeatUpdateType.AFTER_THIS;
    }

    @Override
    @Transactional
    public List<SchedulesModel> handle(SchedulesModel existing, SchedulesModel patch) {
        // 권한 검증 및 타겟 조회
        guard.ensureRepeatable(existing);
        guard.assertOwnerOrAdmin(existing);

        // 일정 시작시간을 기준으로 이후에 있는 일정 조회
        List<SchedulesModel> targets = out.findAfterStartTime(existing.getRepeatGroupId(), existing.getStartTime());

        // 권한 체크
        Long me = SecurityUtil.currentUserId();
        if (targets.stream().anyMatch(s -> !me.equals(s.getMemberId()))) throw guard.notOwner();

        List<SchedulesModel> result = transformTargets(targets,patch,classifier,attachBinder);

        return out.saveAll(result);
    }
}
