package com.example.outbound.openai.config;

import com.example.outbound.openai.dto.OpenAiRequest;
import com.example.outbound.openai.dto.OpenAiResponse;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
public class OpenAiWebClient {

    private final WebClient openAiWebClient;

    @Value("${openai.secret-key}")
    private String apiKey;

    public OpenAiWebClient(@Qualifier("openAiWebClientInternal") WebClient openAiWebClient) {
        this.openAiWebClient = openAiWebClient;
    }

    public OpenAiResponse getChatCompletion(OpenAiRequest request) {
        try {
            return openAiWebClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAiResponse.class)
                    .block();  // 동기 처리

        } catch (WebClientResponseException e) {
            log.error("[OpenAI 호출 실패] status={} body={}", e.getRawStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("OpenAI 호출 실패: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[OpenAI 예외] {}", e.getMessage(), e);
            throw new RuntimeException("OpenAI 처리 중 예외 발생", e);
        }
    }
}
