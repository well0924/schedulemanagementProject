package com.example.exception.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    INVALID_PARAMETER(4001, "파라미터 값을 확인해주세요."),
    NOT_FOUND(4002, "존재하않습니다."),
    INTERNAL_SERVER_ERROR(5000, "서버 에러입니다. 서버 팀에 연락주세요!"),
    DB_ERROR(4003,"데이터베이스에 문제가 발생했습니다."),
    // --- Kafka Event 전용 ---
    EVENT_DUPLICATE(4100, "이미 처리된 이벤트입니다."),
    EVENT_SERIALIZATION_ERROR(4101, "이벤트 직렬화/역직렬화에 실패했습니다."),
    EVENT_PROCESSING_ERROR(4102, "이벤트 처리 중 알 수 없는 오류가 발생했습니다.");

    private final int status;

    private final String message;
}
