package com.example.model.schedules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryModel {
    private Long id;

    private Long memberId;

    private String userMessage;

    private String assistantResponse;

    private LocalDateTime createdAt;
}
