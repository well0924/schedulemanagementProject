package com.example.outbound.openai.fallback;

import com.example.outbound.openai.dto.OpenAiRequest;
import com.example.outbound.openai.dto.OpenAiResponse;

import java.util.List;

public class OpenAiFallback  {

    public static OpenAiResponse fallbackResponse() {
        OpenAiRequest.Message fallbackMsg = OpenAiRequest.Message.builder()
                .role("assistant")
                .content("지금은 추천을 제공할 수 없습니다.")
                .build();

        OpenAiResponse.Choice fallbackChoice = OpenAiResponse.Choice.builder()
                .index(0)
                .message(fallbackMsg)
                .finish_reason("fallback")
                .build();

        return OpenAiResponse.builder()
                .choices(List.of(fallbackChoice))
                .build();
    }
}
