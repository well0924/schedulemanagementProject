package com.example.attach.exception;

import com.example.attach.dto.AttachErrorCode;
import com.example.exception.BaseCustomException;
import com.example.exception.BaseErrorCode;
import lombok.Getter;

@Getter
public class AttachCustomExceptionHandler extends BaseCustomException {

    public AttachCustomExceptionHandler(AttachErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public AttachCustomExceptionHandler(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
