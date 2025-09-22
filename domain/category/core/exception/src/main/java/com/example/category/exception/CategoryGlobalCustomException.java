package com.example.category.exception;

import com.example.category.dto.CategoryErrorCode;
import com.example.exception.dto.ErrorDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.example.service.category")
public class CategoryGlobalCustomException {

    @ExceptionHandler(value = CategoryCustomException.class)
    protected ResponseEntity<ErrorDto> HandleCustomException(CategoryCustomException ex) {
        CategoryErrorCode errorCode = (CategoryErrorCode)ex.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(new ErrorDto(ex.getErrorCode().getCode(), ex.getMessage()));
    }
}
