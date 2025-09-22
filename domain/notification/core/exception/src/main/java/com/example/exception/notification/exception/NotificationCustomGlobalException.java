package com.example.exception.notification.exception;

import com.example.exception.dto.ErrorDto;
import com.example.exception.notification.dto.NotificationErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.example.notification")
public class NotificationCustomGlobalException {

    @ExceptionHandler(value = NotificationCustomException.class)
    protected ResponseEntity<ErrorDto> HandlerCustomException(NotificationCustomException ex){
        NotificationErrorCode errorCode = (NotificationErrorCode)ex.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(new ErrorDto(errorCode.getCode(), errorCode.getMessage()));

    }
}
