package com.example.events.spring;

import com.example.events.kafka.BaseKafkaEvent;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

@Getter
@SuperBuilder
@Jacksonized // Jackson이 SuperBuilder로 역직렬화 (기본 생성자 없음)
public class ChatCompletedEvent extends BaseKafkaEvent {
    Long memberId;
    String userMessage;
    String assistantResponse;
    LocalDateTime createdAt;

}
