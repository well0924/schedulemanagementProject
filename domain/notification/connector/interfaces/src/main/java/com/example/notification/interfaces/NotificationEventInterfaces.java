package com.example.notification.interfaces;

public interface NotificationEventInterfaces<T>{
    //타입별로 추상화
    void handle(T handle);
}
