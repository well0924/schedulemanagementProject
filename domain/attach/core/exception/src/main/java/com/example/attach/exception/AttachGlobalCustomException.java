package com.example.attach.exception;

import com.example.attach.dto.AttachErrorCode;
import com.example.exception.dto.ErrorDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.example.service.attach")
public class AttachGlobalCustomException {

    @ExceptionHandler(AttachCustomExceptionHandler.class)
    public ResponseEntity<ErrorDto> handleAttachException(AttachCustomExceptionHandler ex) {
        AttachErrorCode error = (AttachErrorCode) ex.getErrorCode();
        return ResponseEntity
                .status(error.getHttpStatus())
                .body(new ErrorDto(error.getCode(), ex.getMessage()));
    }
}
