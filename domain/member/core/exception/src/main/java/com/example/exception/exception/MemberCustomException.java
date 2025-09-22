package com.example.exception.exception;

import com.example.exception.BaseCustomException;
import com.example.exception.BaseErrorCode;
import com.example.exception.dto.MemberErrorCode;
import lombok.Getter;

@Getter
public class MemberCustomException extends BaseCustomException {

    public MemberCustomException(String message, MemberErrorCode errorCode) {
        super(errorCode, message);
    }

    public MemberCustomException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
