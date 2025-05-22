package com.example.interfaces.notification.kafka;

public interface KafkaEventConsumer<T> {
    void handle(T event);
}
