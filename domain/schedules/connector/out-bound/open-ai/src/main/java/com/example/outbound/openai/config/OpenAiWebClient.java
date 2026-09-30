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

import java.time.Duration;

@Slf4j
@Component
public class OpenAiWebClient {

    private final WebClient openAiWebClient;

    @Value("${openai.secret-key}")
    private String apiKey;

    private final ObjectMapper objectMapper;

    // 스트리밍 전용 서킷브레이커 (application-openai.yml의 instances.openAiChat 설정을 사용)
    public static final String CHAT_CIRCUIT_BREAKER = "openAiChat";

    private final CircuitBreaker circuitBreaker;

    // 첫 SSE 청크까지 허용 시간, 이후 청크 사이 허용 시간
    private final Duration firstChunkTimeout;
    private final Duration idleChunkTimeout;

    public OpenAiWebClient(@Qualifier("openAiWebClientInternal") WebClient openAiWebClient,
                           ObjectMapper objectMapper,
                           CircuitBreakerRegistry registry,
                           @Value("${openai.stream.first-chunk-timeout-ms:10000}") long firstChunkTimeoutMs,
                           @Value("${openai.stream.idle-chunk-timeout-ms:15000}") long idleChunkTimeoutMs) {
        this.openAiWebClient = openAiWebClient;
        this.objectMapper = objectMapper;
        this.circuitBreaker = registry.circuitBreaker(CHAT_CIRCUIT_BREAKER);
        this.firstChunkTimeout = Duration.ofMillis(firstChunkTimeoutMs);
        this.idleChunkTimeout = Duration.ofMillis(idleChunkTimeoutMs);
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
    // 실패(HTTP 오류, 청크 타임아웃, 서킷 OPEN)는 그대로 에러로 내보낸다.
    // 대체 응답은 호출하는 쪽(ChatBotService)이 만든다. 여기서 대체 토큰을 섞으면
    // 정상 응답처럼 흘러가 대화 이력에 AI 답변으로 저장되기 때문이다.
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
                // 첫 청크가 늦거나 도중에 끊기면 실패로 본다 (서킷브레이커 실패로 집계되도록 CB보다 앞에 둔다)
                .timeout(Mono.delay(firstChunkTimeout), chunk -> Mono.delay(idleChunkTimeout))
                .filter(data -> !data.isBlank() && !data.equals("[DONE]"))
                .mapNotNull(this::extractToken);

        return chatFlux
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .doOnError(e -> log.warn("[OpenAI 스트리밍 실패] circuit={}, reason={}",
                        circuitBreaker.getState(), e.toString()));
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
