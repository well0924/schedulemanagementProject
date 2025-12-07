package com.example.events.outbox;

public enum OutboxStatus {
    PENDING,   // 발행 대기
    SENT,      // 발행 성공
    FAILED     // 발행 불가 (재시도 exceed)
}
