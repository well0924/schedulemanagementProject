package com.example.apimodel.schedule;

public record ChatRequest(
        Long memberId,
        String message
) {
}
