package com.example.service.schedule.domainService.repeat.update;

import com.example.enumerate.schedules.RepeatUpdateType;
import com.example.model.schedules.SchedulesModel;
import com.example.service.schedule.domainService.support.AttachBinder;
import com.example.service.schedule.domainService.support.ScheduleClassifier;
import com.example.service.schedule.domainService.support.SchedulePatchApplier;

import java.util.List;

public interface RepeatUpdateHandler {
    // 일정 수정 타입
    RepeatUpdateType type();
    // 일정 수정 타입에 따른 로직처리
    List<SchedulesModel> handle(SchedulesModel existing, SchedulesModel patch);

    // 일괄 가공하는 공통 메서드
    default List<SchedulesModel> transformTargets(
            List<SchedulesModel> targets,
            SchedulesModel patch,
            ScheduleClassifier classifier,
            AttachBinder attachBinder) {
        return targets.stream()
                .map(target -> {
                    SchedulesModel updated = SchedulePatchApplier.apply(target, patch);
                    updated = classifier.normalizeAndClassify(updated);
                    updated = attachBinder.handleAttachUpdate(target, updated);
                    updated.updateProgressStatus();
                    return updated;
                }).toList();
    }
}
