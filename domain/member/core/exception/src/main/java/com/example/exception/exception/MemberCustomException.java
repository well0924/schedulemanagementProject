package com.example.exception.exception;

import com.example.exception.dto.MemberErrorCode;
import lombok.Getter;

@Getter
public class MemberCustomException extends RuntimeException {

    private final MemberErrorCode memberErrorCode;

    public MemberCustomException(String message, MemberErrorCode errorCode) {
        super(message);
        this.memberErrorCode = errorCode;
    }

    public MemberCustomException(MemberErrorCode errorCode) {
        super(errorCode.getMessage());
        this.memberErrorCode = errorCode;
    }

    public MemberErrorCode getMemberErrorCode() {
        return memberErrorCode;
    }
}
