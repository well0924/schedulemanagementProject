package com.example.exception.notification.dto;

import com.example.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements BaseErrorCode {

    NOTIFICATION_EMPTY(HttpStatus.NOT_FOUND,40025,"알림목록이 없습니다"),
    INVALID_NOTIFICATION(HttpStatus.NOT_FOUND,40026,"알림을 찾을 수 없습니다.");


    private final HttpStatus httpStatus;

    private final int code;

    private final String message;

}
