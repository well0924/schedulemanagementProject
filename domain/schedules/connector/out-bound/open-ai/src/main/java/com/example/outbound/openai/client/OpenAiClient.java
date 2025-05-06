package com.example.outbound.openai.client;

import com.example.outbound.openai.config.OpenAiConfig;
import com.example.outbound.openai.config.OpenAiErrorDecoder;
import com.example.outbound.openai.dto.OpenAiRequest;
import com.example.outbound.openai.dto.OpenAiResponse;
import com.example.outbound.openai.fallback.OpenAiFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "OpenAi",
        url="https://api.openai.com/v1",
        configuration = {OpenAiConfig.class, OpenAiErrorDecoder.class},
        fallback = OpenAiFallback.class
)
public interface OpenAiClient {

    @PostMapping(value = "/chat/completions", consumes = "application/json")
    OpenAiResponse getChatCompletion(@RequestBody OpenAiRequest request);
}
