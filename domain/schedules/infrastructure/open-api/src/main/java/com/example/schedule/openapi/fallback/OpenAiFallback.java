package com.example.schedule.openapi.fallback;

import com.example.schedule.openapi.client.OpenAiClient;
import com.example.schedule.openapi.dto.OpenAiRequest;
import com.example.schedule.openapi.dto.OpenAiResponse;

import java.util.List;

public class OpenAiFallback implements OpenAiClient {

    @Override
    public OpenAiResponse getChatCompletion(OpenAiRequest request) {
        OpenAiRequest.Message fallbackMsg = OpenAiRequest
                .Message
                .builder()
                .role("assistant")
                .content("지금은 추천을 제공할 수 없습니다.")
                .build();

        OpenAiResponse.Choice fallbackChoice = OpenAiResponse
                .Choice
                .builder()
                .index(0)
                .message(fallbackMsg)
                .finish_reason("fallback")
                .build();

        return OpenAiResponse
                .builder()
                .choices(List.of(fallbackChoice))
                .build();
    }
}
