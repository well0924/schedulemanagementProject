package com.example.events.spring;

import com.example.events.kafka.BaseKafkaEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatCompletedEvent extends BaseKafkaEvent {
    Long memberId;
    String userMessage;
    String assistantResponse;
    LocalDateTime createdAt;

}
