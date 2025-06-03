package com.example.interfaces.notification.kafka;

public interface KafkaDlqConsumer {

    void consume(String message);
}
