package com.example.exception.global;

import com.example.exception.BaseCustomException;
import com.example.exception.dto.ErrorCode;
import com.example.exception.dto.ErrorDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 전역 예외 처리. 도메인 예외(BaseCustomException)도 여기서 처리한다.
// 예외: ScheduleCustomException은 BaseCustomException이 아니므로 ScheduleCustomGlobalException(@Order(0))이 먼저 처리
// 주의: @Order가 없는 Advice는 LOWEST_PRECEDENCE라 이 클래스보다 뒤에 오고,
//      아래 RuntimeException 핸들러가 먼저 잡기 때문에 절대 실행되지 않는다.
@Slf4j
@Order(1)
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
        log.info(message);
        return ResponseEntity
                .badRequest()
                .body(new ErrorDto(ErrorCode.INVALID_PARAMETER.getStatus(), message));
    }

    // 도메인 예외 공통 처리 (Member/Category/Attach/Notification)
    // 각 도메인 ErrorCode(BaseErrorCode)의 상태코드·코드·메시지를 그대로 응답
    @ExceptionHandler({BaseCustomException.class})
    protected ResponseEntity<ErrorDto> HandleCustomException(BaseCustomException ex) {
        log.warn("도메인 예외 [{}]", ex.getErrorCode().getCode(), ex);
        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(new ErrorDto(ex.getErrorCode().getCode(), ex.getMessage()));
    }

    // 요청 바디 파싱 실패 (잘못된 JSON, enum 형식 불일치 등) → 클라이언트 오류이므로 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDto> handleNotReadable(HttpMessageNotReadableException ex) {
        log.info("요청 바디 파싱 실패: {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(new ErrorDto(ErrorCode.INVALID_PARAMETER.getStatus(), ErrorCode.INVALID_PARAMETER.getMessage()));
    }

    // 일정 외 도메인의 DB 제약 위반 (중복키 등) → 409
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorDto> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("DB 제약 위반", ex);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorDto(ErrorCode.DB_ERROR.getStatus(), ErrorCode.DB_ERROR.getMessage()));
    }

    // 예상 못 한 런타임 (500)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorDto> handleUnexpected(RuntimeException ex) {
        // 로그는 꼭 찍어야 함
        log.error("처리되지 않은 런타임 예외", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                new ErrorDto(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "서버 내부 오류가 발생했습니다.")
        );
    }
}
