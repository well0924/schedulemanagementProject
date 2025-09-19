package com.example.exception.schedules.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ScheduleErrorCode {
    // 클라이언트 에러
    INVALID_PROGRESS_STATUS(HttpStatus.BAD_REQUEST, 40020, "잘못된 일정 상태값입니다."),
    SCHEDULE_EMPTY(HttpStatus.BAD_REQUEST, 40021, "현재 일정이 없습니다."),
    USER_NOT_AUTHORIZED(HttpStatus.FORBIDDEN, 40301, "사용자에게 권한이 없습니다."),
    SCHEDULE_COMPLETED(HttpStatus.BAD_REQUEST, 40022, "이미 완료된 일정입니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, 40023, "해당 일정'%s'을 찾을 수 없습니다."),
    SCHEDULE_TIME_CONFLICT(HttpStatus.CONFLICT, 40024, "해당 일정은 기존의 일정과 충돌이 납니다."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, 40025, "유효하지 않은 시간범위입니다."),

    // 서버 에러
    SCHEDULE_CREATED_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, 50020, "일정 생성에 실패했습니다."),
    NOT_START_TIME_AND_END_TIME(HttpStatus.INTERNAL_SERVER_ERROR, 50021, "시작 시간과 종료 시간이 설정되지 않았습니다."),
    START_TIME_AFTER_END_TIME_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, 50022, "시작 시간은 종료 시간보다 이후일 수 없습니다."),
    INVALID_DELETE_TYPE_FOR_NON_REPEATED(HttpStatus.INTERNAL_SERVER_ERROR, 50023, "유효하지 않은 삭제 유형입니다."),
    NOT_SCHEDULE_OWNER(HttpStatus.INTERNAL_SERVER_ERROR, 50024, "현재 사용자가 아닙니다."),
    INVALID_OWNER_FOR_BULK(HttpStatus.INTERNAL_SERVER_ERROR, 50025, "일정선택삭제에 유효하지 않은 사용자입니다."),
    SCHEDULE_UPDATED_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, 50026, "일정 수정에 실패했습니다."),
    SCHEDULE_DELETE_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, 50027, "일정 삭제에 실패했습니다.");

    private final HttpStatus httpStatus;

    private final int status;

    private final String message;

    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}
