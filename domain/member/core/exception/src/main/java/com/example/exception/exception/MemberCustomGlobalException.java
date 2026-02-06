package com.example.exception.exception;

import com.example.exception.dto.ErrorDto;
import com.example.exception.dto.MemberErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class MemberCustomGlobalException {

    @ExceptionHandler(value = MemberCustomException.class)
    protected ResponseEntity<ErrorDto> HandleCustomException(MemberCustomException ex) {
        MemberErrorCode error = (MemberErrorCode) ex.getErrorCode();
        return ResponseEntity
                .status(error.getHttpStatus())
                .body(new ErrorDto(error.getCode(), ex.getMessage()));
    }

}
