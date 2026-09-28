package com.example.controller.schedule;

import com.example.category.dto.CategoryErrorCode;
import com.example.category.exception.CategoryCustomException;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ExceptionAdviceRoutingTest 전용. 일정 컨트롤러 패키지(com.example.controller.schedule)에 있어야
// ScheduleCustomGlobalException(basePackages)이 적용된다.
// standalone MockMvc 등록에도 @Controller 계열 애노테이션이 필요하다.
@RestController
@RequestMapping("/test/schedule")
public class ExceptionRoutingScheduleController {

    @GetMapping("/schedule-exception")
    public void scheduleException() {
        throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_NOT_FOUND, 10L);
    }

    @GetMapping("/invalid-time")
    public void invalidTime() {
        throw new ScheduleCustomException(ScheduleErrorCode.START_TIME_AFTER_END_TIME_EXCEPTION);
    }

    @GetMapping("/bulk-not-owner")
    public void bulkNotOwner() {
        throw new ScheduleCustomException(ScheduleErrorCode.INVALID_OWNER_FOR_BULK);
    }

    @GetMapping("/not-owner")
    public void notOwner() {
        throw new ScheduleCustomException(ScheduleErrorCode.NOT_SCHEDULE_OWNER);
    }

    @GetMapping("/db-violation")
    public void dbViolation() {
        throw new DataIntegrityViolationException("uk_member_starttime");
    }

    // 일정 수정 경로에서 카테고리 검증 실패하는 경우 (ScheduleOutConnector.validateScheduleData)
    @GetMapping("/category-exception")
    public void categoryException() {
        throw new CategoryCustomException(CategoryErrorCode.INVALID_PARENT_CATEGORY);
    }
}
