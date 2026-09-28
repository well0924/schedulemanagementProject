package com.example.interfaces.notification.kafka;

import org.springframework.kafka.support.Acknowledgment;

// DLQ 컨슈머는 기본 리스너 팩토리(StringDeserializer, ack-mode MANUAL)를 사용하므로
// 문자열로 받아 직접 역직렬화하고, 처리 후 직접 커밋해야 한다.
public interface KafkaDlqConsumer {

    void consume(String message, Acknowledgment ack);
}
