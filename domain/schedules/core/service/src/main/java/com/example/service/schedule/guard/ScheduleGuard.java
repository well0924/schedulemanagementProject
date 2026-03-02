package com.example.service.schedule.guard;

import com.example.category.dto.CategoryErrorCode;
import com.example.category.exception.CategoryCustomException;
import com.example.exception.dto.MemberErrorCode;
import com.example.exception.exception.MemberCustomException;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import com.example.interfaces.category.CategoryRepositoryPort;
import com.example.interfaces.member.MemberRepositoryPort;
import com.example.model.schedules.SchedulesModel;
import com.example.security.config.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ScheduleGuard {

    private final MemberRepositoryPort memberRepositoryPort;
    private final CategoryRepositoryPort categoryRepositoryPort;

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

    // 일정 생성시 데이터 검증
    public void validateCreation(List<SchedulesModel> models) {
        if (models.isEmpty()) return;

        SchedulesModel head = models.get(0);

        // 1. 기초 데이터 존재 여부 (N번 호출 방지)
        if (!memberRepositoryPort.existsById(head.getMemberId())) {
            throw new MemberCustomException(MemberErrorCode.NOT_FIND_USERID);
        }
        if (head.getCategoryId() != null && !categoryRepositoryPort.existsById(head.getCategoryId())) {
            throw new CategoryCustomException(CategoryErrorCode.INVALID_PARENT_CATEGORY);
        }

        // 2. 시간 선후 관계 체크
        for (SchedulesModel m : models) {
            if (m.getStartTime().isAfter(m.getEndTime())) {
                throw new ScheduleCustomException(ScheduleErrorCode.START_TIME_AFTER_END_TIME_EXCEPTION);
            }
        }
    }
}
