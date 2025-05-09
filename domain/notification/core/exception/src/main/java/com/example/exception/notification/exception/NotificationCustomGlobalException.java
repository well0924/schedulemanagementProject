package com.example.exception.notification.exception;

import com.example.exception.notification.dto.NotificationErrorDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class NotificationCustomGlobalException {

    @ExceptionHandler(value = NotificationCustomException.class)
    protected ResponseEntity<NotificationErrorDto> HandlerCustomException(NotificationCustomException ex){
        return new ResponseEntity<>(
                NotificationErrorDto
                        .builder()
                        .errorCode(ex.getNotificationErrorCode().getStatus())
                        .message(ex.getMessage())
                        .build(), HttpStatus.valueOf(ex.getErrorCode().getStatus())
        );
    }
}
