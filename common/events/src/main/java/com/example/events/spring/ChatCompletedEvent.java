package com.example.events.spring;

import com.example.events.kafka.BaseKafkaEvent;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@SuperBuilder
public class ChatCompletedEvent extends BaseKafkaEvent {
    Long memberId;
    String userMessage;
    String assistantResponse;
    LocalDateTime createdAt;

}
