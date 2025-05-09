package com.example.exception.notification.exception;

import com.example.exception.notification.dto.NotificationErrorCode;
import lombok.Getter;

@Getter
public class NotificationCustomException extends RuntimeException {

    private NotificationErrorCode notificationErrorCode;

    public  NotificationCustomException(NotificationErrorCode notificationErrorCode) {
        super(notificationErrorCode.getMessage());
        this.notificationErrorCode = notificationErrorCode;
    }

    public NotificationErrorCode getErrorCode(){
        return notificationErrorCode;
    }
}
