package com.example.service.schedule.recommend;

import com.example.outbound.openai.dto.OpenAiRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OpenAiRequestBuilder {

    /**
     * 프롬프트 문자열로 OpenAI 요청 객체를 생성한다.
     */
    public OpenAiRequest build(String prompt) {
        return OpenAiRequest.builder()
                .model("gpt-4o")
                .messages(List.of(OpenAiRequest.Message.builder()
                        .role("user")
                        .content(prompt)
                        .build()))
                .build();
    }
}
