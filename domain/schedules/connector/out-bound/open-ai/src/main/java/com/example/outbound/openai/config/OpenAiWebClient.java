package com.example.outbound.openai.config;

import com.example.outbound.openai.dto.OpenAiRequest;
import com.example.outbound.openai.dto.OpenAiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class OpenAiWebClient {

    private final WebClient openAiWebClient;

    @Value("${openai.secret-key}")
    private String apiKey;

    public OpenAiWebClient(@Qualifier("openAiWebClientInternal") WebClient openAiWebClient) {
        this.openAiWebClient = openAiWebClient;
    }

    public Mono<OpenAiResponse> getChatCompletion(OpenAiRequest request) {
        return openAiWebClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("[OpenAI 응답 오류] status={} body={}", response.statusCode(), body);
                                    return Mono.error(new RuntimeException("OpenAI 응답 오류: " + body));
                                })
                )
                .bodyToMono(OpenAiResponse.class)
                .doOnError(e -> log.error("[OpenAI 호출 실패] {}", e.getMessage(), e));
    }
}
