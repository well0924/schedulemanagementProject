package com.example.category.dto;

import com.example.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CategoryErrorCode implements BaseErrorCode {

    //category error
    NOT_FOUND_CATEGORY(HttpStatus.NOT_FOUND,40009, "카테고리 '%s'를 찾을 수 없습니다."),
    DUPLICATED_CATEGORY_NAME(HttpStatus.CONFLICT,40010, "카테고리 이름 '%s'이(가) 중복됩니다."),
    INVALID_PARENT_CATEGORY(HttpStatus.BAD_REQUEST,40011, "유효하지 않은 부모 카테고리입니다."),
    CANNOT_DELETE_CATEGORY_WITH_CHILDREN(HttpStatus.BAD_REQUEST,40012, "자식 카테고리와 함께 삭제할 수 없습니다.");

    private final HttpStatus httpStatus;

    private final int code;

    private final String message;
    //동적 메시지 작성
    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}
