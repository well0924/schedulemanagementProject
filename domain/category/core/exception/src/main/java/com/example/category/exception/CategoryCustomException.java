package com.example.category.exception;

import com.example.category.dto.CategoryErrorCode;
import com.example.exception.BaseCustomException;
import lombok.Getter;

@Getter
public class CategoryCustomException extends BaseCustomException {

    // 동적 메시지용
    public CategoryCustomException(CategoryErrorCode errorCode, Object... args) {
        super(errorCode, errorCode.formatMessage(args));
    }

    // 기본 메시지용
    public CategoryCustomException(CategoryErrorCode errorCode) {
        super(errorCode);
    }
}
