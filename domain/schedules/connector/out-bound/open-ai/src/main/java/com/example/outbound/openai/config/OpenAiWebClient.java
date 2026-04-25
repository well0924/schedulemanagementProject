package com.example.outbound.openai.config;

import com.example.outbound.openai.dto.OpenAiRequest;
import com.example.outbound.openai.dto.OpenAiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
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

    private final CircuitBreaker circuitBreaker;


    public OpenAiWebClient(@Qualifier("openAiWebClientInternal") WebClient openAiWebClient,
                           ObjectMapper objectMapper,
                           CircuitBreakerRegistry registry) {
        this.openAiWebClient = openAiWebClient;
        this.objectMapper = objectMapper;
        this.circuitBreaker = registry.circuitBreaker("openail.yml");
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
        Flux<String> chatFlux = openAiWebClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException("OpenAI 오류: " + body)))
                )
                .bodyToFlux(String.class)
                .filter(data -> !data.isBlank() && !data.equals("[DONE]"))
                .mapNotNull(this::extractToken);
        // Flux 전체 스트림에 대해 서킷 브레이커 적용
        return chatFlux
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker)) // 서킷 브레이커 적용
                .onErrorResume(e -> {
                    log.error("[OpenAI 서킷 오픈 또는 에러 발생] Fallback 메시지 반환. 사유: {}", e.getMessage());
                    return Flux.just("죄송합니다. ", "현재 ", "AI ", "연결이 ", "원활하지 ", "않습니다. ",
                            "잠시 ", "후 ", "다시 ", "시도해 ", "주세요.");
                });
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
