package com.example.exception;

import org.springframework.http.HttpStatus;

public interface BaseErrorCode {

    HttpStatus getHttpStatus();

    int getCode();

    String getMessage();

    default String formatMessage(Object... args) {
        return String.format(getMessage(), args);
    }
}
