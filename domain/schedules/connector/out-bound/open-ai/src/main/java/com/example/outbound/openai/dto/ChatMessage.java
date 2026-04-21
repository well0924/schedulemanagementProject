package com.example.outbound.openai.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ChatMessage(
        String role,          // "user" | "assistant" | "system"
        String content,
        LocalDateTime createdAt
) {
}
