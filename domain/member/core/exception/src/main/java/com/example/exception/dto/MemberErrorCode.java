package com.example.exception.dto;

import com.example.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements BaseErrorCode {

    //user error
    USERID_DUPLICATE(HttpStatus.CONFLICT,40001,"회원 아이디가 중복이 됩니다."),
    USER_EMAIL_DUPLICATE(HttpStatus.CONFLICT,40002,"회원 이메일이 중복이 됩니다."),
    NOT_FIND_USERID(HttpStatus.NOT_FOUND,40003,"회원아이디를 찾을 수가 없습니다."),
    NOT_SEARCH_USER(HttpStatus.NOT_FOUND,4006,"검색된 회원이 없습니다."),
    NOT_USER(HttpStatus.NOT_FOUND,4007,"회원이 존재하지 않습니다."),
    NOT_PASSWORD_MATCH(HttpStatus.NOT_FOUND,4008,"비밀번호가 일치하지 않습니다.");

    private final HttpStatus httpStatus;

    private final int code;

    private final String message;
}
