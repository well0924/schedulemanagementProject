package com.example.exception.schedules.exception;

import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.dto.ScheduleErrorDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ScheduleCustomGlobalException {

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
}
