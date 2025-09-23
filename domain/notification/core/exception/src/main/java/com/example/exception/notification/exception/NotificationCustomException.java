package com.example.exception.notification.exception;

import com.example.exception.BaseCustomException;
import com.example.exception.BaseErrorCode;
import com.example.exception.notification.dto.NotificationErrorCode;
import lombok.Getter;

@Getter
public class NotificationCustomException extends BaseCustomException {

    public  NotificationCustomException(NotificationErrorCode notificationErrorCode,String message) {
        super(notificationErrorCode,message);
    }

    public  NotificationCustomException(BaseErrorCode notificationErrorCode) {
        super(notificationErrorCode);
    }
}
