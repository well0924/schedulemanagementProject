package com.example.exception.schedules.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScheduleErrorCode {
    INVALID_PROGRESS_STATUS(40020,"잘못된 일정 상태값입니다."),
    SCHEDULE_EMPTY(40021,"현재 일정이 없습니다."),
    USER_NOT_AUTHORIZED(40301,"사용자에게 권한이 없습니다."),
    SCHEDULE_COMPLETED(40022,"이미 완료된 일정입니다."),
    SCHEDULE_NOT_FOUND(40023,"해당 일정'%s'을 찾을 수 없습니다."),
    SCHEDULE_TIME_CONFLICT(40024,"해당이 일정은 기존의 일정과 충돌이 납니다."),
    INVALID_TIME_RANGE(40025,"유효하지 않은 시간범위입니다."),
    SCHEDULE_CREATED_FAIL(50020,"일정 생성에 실패를 했습니다."),
    NOT_START_TIME_AND_END_TIME(50021,"시작 시간과 종료 시간이 설정되지 않았습니다."),
    START_TIME_AFTER_END_TIME_EXCEPTION(50022,"시작 시간은 종료 시간보다 이후일 수 없습니다."),
    INVALID_DELETE_TYPE_FOR_NON_REPEATED(50023,"유효하지 않은 삭제 유형입니다.");

    private final int status;

    private final String message;

    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}
