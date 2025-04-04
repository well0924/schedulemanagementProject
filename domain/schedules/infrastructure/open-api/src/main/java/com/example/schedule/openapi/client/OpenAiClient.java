package com.example.schedule.openapi.client;

import com.example.schedule.openapi.config.OpenApiConfig;
import com.example.schedule.openapi.dto.OpenAiRequest;
import com.example.schedule.openapi.dto.OpenAiResponse;
import com.example.schedule.openapi.fallback.OpenAiFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@FeignClient(
        name = "OpenAi",
        url = "https://api.openai.com/v1",
        configuration = OpenApiConfig.class,
        fallback = OpenAiFallback.class)
public interface OpenAiClient {

    @PostMapping(value = "/chat/completions", consumes = "application/json")
    OpenAiResponse getChatCompletion(@RequestBody OpenAiRequest request);
}
