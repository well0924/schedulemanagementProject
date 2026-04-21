package com.example.interfaces.notification.chatbot;

import com.example.events.spring.ChatCompletedEvent;
import com.example.interfaces.notification.event.NotificationEventInterfaces;

public interface ChatEventPort extends NotificationEventInterfaces<ChatCompletedEvent> {

    // ChatBotService에 사용할 메서드
    default void publish(ChatCompletedEvent event) {
        handle(event);  // 내부적으로 handle 호출
    }
}
