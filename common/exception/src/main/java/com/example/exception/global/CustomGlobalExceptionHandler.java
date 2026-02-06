package com.example.exception.global;

import com.example.exception.BaseCustomException;
import com.example.exception.dto.ErrorCode;
import com.example.exception.dto.ErrorDto;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 도메인 Advice보다 우선순위 낮게
@Order(2)
@RestControllerAdvice
public class CustomGlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleValidationException(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다.");

        return ResponseEntity
                .badRequest()
                .body(new ErrorDto(ErrorCode.INVALID_PARAMETER.getStatus(), message));
    }

    // 나머지 도메인 모듈 400 처리 부분
    @ExceptionHandler({BaseCustomException.class})
    protected ResponseEntity<ErrorDto> HandleCustomException(BaseCustomException ex) {
        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(new ErrorDto(ex.getErrorCode().getCode(), ex.getMessage()));
    }

    // 예상 못 한 런타임 (500)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorDto> handleUnexpected(RuntimeException ex) {
        // 로그는 꼭 찍어야 함
        ex.printStackTrace(); // 실제로는 Logger.error()로
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                new ErrorDto(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "서버 내부 오류가 발생했습니다.")
        );
    }
}
