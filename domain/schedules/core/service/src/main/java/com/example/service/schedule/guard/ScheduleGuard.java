package com.example.service.schedule.guard;

import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import com.example.model.schedules.SchedulesModel;
import com.example.service.auth.SecurityUtil;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ScheduleGuard {

    // 반복이 아닌 일정에 반복 삭제 요청 방어
    public void validateRepeatDelete(SchedulesModel model) {
        if (model.getRepeatGroupId() == null || model.getRepeatGroupId().isBlank()) {
            throw new ScheduleCustomException(ScheduleErrorCode.INVALID_DELETE_TYPE_FOR_NON_REPEATED);
        }
    }

    // 반복이 아닌 일정에 '업데이트용' 반복 요청 방어
    public void ensureRepeatable(SchedulesModel existing){
        if (existing.getRepeatGroupId() == null || existing.getRepeatGroupId().isBlank()) {
            throw new ScheduleCustomException(ScheduleErrorCode.INVALID_DELETE_TYPE_FOR_NON_REPEATED);
        }
    }
    
    // 권한 체크
    public void assertOwnerOrAdmin(SchedulesModel schedule) {
        if (SecurityUtil.hasRole("ADMIN")) return;
        var me = SecurityUtil.currentUserId();
        if (!Objects.equals(schedule.getMemberId(), me)) {
            throw new ScheduleCustomException(ScheduleErrorCode.NOT_SCHEDULE_OWNER);
        }
    }

    // 권한이 아닌 경우
    public ScheduleCustomException notOwner() {
        return new ScheduleCustomException(ScheduleErrorCode.NOT_SCHEDULE_OWNER);
    }
}
