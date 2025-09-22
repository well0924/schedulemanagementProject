package com.example.exception.schedules.exception;

import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.dto.ScheduleErrorDto;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(basePackages = "com.example")
public class ScheduleCustomGlobalException {

    // 일정 모듈 예외만 (409, 400)
    @ExceptionHandler(value = ScheduleCustomException.class)
    protected ResponseEntity<ScheduleErrorDto> HandleCustomException(ScheduleCustomException ex) {
        ScheduleErrorCode error = ex.getScheduleErrorCode();
        return new ResponseEntity<>(
                ScheduleErrorDto
                        .builder()
                        .errorCode(ex.getScheduleErrorCode().getStatus())
                        .message(ex.getMessage())
                        .build(),
                error.getHttpStatus());
    }

    // DB 제약 위반 (중복키, 외래키 충돌 등)
    @ExceptionHandler({DataIntegrityViolationException.class, ConstraintViolationException.class})
    public ResponseEntity<ScheduleErrorDto> handleDb(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT) // 409로 고정
                .body(ScheduleErrorDto.builder()
                        .errorCode(40024) // SCHEDULE_TIME_CONFLICT 코드 그대로
                        .message("해당 일정은 기존의 일정과 충돌이 납니다.")
                        .build());
    }
}
