package com.example.exception.notification.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode {

    NOTIFICATION_EMPTY(40025,"알림목록이 없습니다"),
    INVALID_NOTIFICATION(40026,"알림을 찾을 수 없습니다.");

    private final int status;

    private final String message;

}
