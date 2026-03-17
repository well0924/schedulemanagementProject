package com.example.interfaces.notification.kafka;

import org.springframework.kafka.support.Acknowledgment;

public interface KafkaEventConsumer<T> {
    void handle(T event, Acknowledgment ack);
}
