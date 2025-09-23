package com.example.exception;

import lombok.Getter;

@Getter
public class BaseCustomException extends RuntimeException {

    private final BaseErrorCode errorCode;

    public BaseCustomException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BaseCustomException(BaseErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
