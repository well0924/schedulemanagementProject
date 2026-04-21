package com.example.outbound.openai.config;

import com.example.outbound.openai.dto.OpenAiRequest;
import com.example.outbound.openai.dto.OpenAiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class OpenAiWebClient {

    private final WebClient openAiWebClient;

    @Value("${openai.secret-key}")
    private String apiKey;

    private final ObjectMapper objectMapper;

    public OpenAiWebClient(@Qualifier("openAiWebClientInternal") WebClient openAiWebClient, ObjectMapper objectMapper) {
        this.openAiWebClient = openAiWebClient;
        this.objectMapper = objectMapper;
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

    // 챗봇 스트리밍
    public Flux<String> streamChatCompletion(OpenAiRequest request) {
        return openAiWebClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)     // SSE 스트리밍
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("[OpenAI 스트리밍 오류] status={} body={}",
                                            response.statusCode(), body);
                                    return Mono.error(new RuntimeException("OpenAI 스트리밍 오류: " + body));
                                })
                )
                .bodyToFlux(String.class)
                .filter(data -> !data.isBlank() && !data.equals("[DONE]"))
                .mapNotNull(this::extractToken)
                .doOnError(e -> log.error("[OpenAI 스트리밍 실패] {}", e.getMessage(), e));
    }

    // SSE 데이터에서 텍스트 토큰만 추출
    private String extractToken(String raw) {
        try {
            // "data: {...}" 형식에서 JSON 부분만 추출
            String json = raw.startsWith("data:") ? raw.substring(5).trim() : raw;
            JsonNode node = objectMapper.readTree(json);
            JsonNode content = node
                    .path("choices")
                    .get(0)
                    .path("delta")
                    .path("content");
            if (content.isMissingNode() || content.isNull()) return null;
            return content.asText();
        } catch (Exception e) {
            log.warn("[토큰 추출 실패] raw={}, 이유={}", raw, e.getMessage());
            return null;
        }
    }
}
