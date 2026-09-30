package com.example.webclient;

import com.example.outbound.openai.config.OpenAiWebClient;
import com.example.outbound.openai.dto.OpenAiRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

// 챗봇 스트리밍 서킷브레이커가 실제 HTTP 실패에 반응하는지 확인 (가짜 OpenAI = WireMock)
class OpenAiChatCircuitBreakerTest {

    private static final String SSE_OK =
            "data: {\"choices\":[{\"delta\":{\"content\":\"안녕\"}}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{\"content\":\"하세요\"}}]}\n\n" +
            "data: [DONE]\n\n";

    private WireMockServer wm;
    private CircuitBreakerRegistry registry;
    private OpenAiWebClient client;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();

        // application-openai.yml의 openAiChat과 같은 판단 기준 (창 10, 최소 5회, 실패율 50%)
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
        registry = CircuitBreakerRegistry.of(config);

        // 청크 타임아웃은 넉넉히 둔다. 짧게 두면 첫 요청의 연결 준비 시간 때문에 500 대신 타임아웃으로 실패한다.
        client = newClient(5000);
    }

    private OpenAiWebClient newClient(long chunkTimeoutMs) {
        WebClient webClient = WebClient.builder().baseUrl("http://localhost:" + wm.port()).build();
        return new OpenAiWebClient(webClient, new ObjectMapper(), registry, chunkTimeoutMs, chunkTimeoutMs);
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    private CircuitBreaker chatBreaker() {
        return registry.circuitBreaker(OpenAiWebClient.CHAT_CIRCUIT_BREAKER);
    }

    @Test
    @DisplayName("정상 SSE 응답은 토큰만 뽑아 흘려보내고 성공으로 기록된다")
    void 정상_스트림() {
        wm.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(SSE_OK)));

        StepVerifier.create(client.streamChatCompletion(new OpenAiRequest()))
                .expectNext("안녕", "하세요")
                .verifyComplete();

        assertThat(chatBreaker().getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("500이 5번 연속되면 서킷이 열리고, 이후 호출은 OpenAI로 나가지 않는다")
    void 연속_실패_시_서킷_오픈() {
        wm.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"boom\"}")));

        for (int i = 0; i < 5; i++) {
            StepVerifier.create(client.streamChatCompletion(new OpenAiRequest()))
                    .expectError()
                    .verify(Duration.ofSeconds(5));
        }
        assertThat(chatBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        StepVerifier.create(client.streamChatCompletion(new OpenAiRequest()))
                .expectError(CallNotPermittedException.class)
                .verify(Duration.ofSeconds(5));

        // 서킷이 열린 뒤의 6번째 호출은 HTTP 요청 자체가 없어야 한다
        wm.verify(5, postRequestedFor(urlEqualTo("/chat/completions")));
    }

    @Test
    @DisplayName("첫 청크가 제한 시간 안에 오지 않으면 타임아웃 실패로 기록된다")
    void 첫_청크_타임아웃() {
        wm.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(SSE_OK)
                        .withFixedDelay(1500))); // 첫 청크 제한 500ms보다 길게

        StepVerifier.create(newClient(500).streamChatCompletion(new OpenAiRequest()))
                .expectError(TimeoutException.class)
                .verify(Duration.ofSeconds(5));

        assertThat(chatBreaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("application-openai.yml에 스트리밍 전용 서킷 설정(openAiChat)이 있다")
    void 스트리밍_서킷_설정_존재() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-openai.yml"));
        Properties p = yaml.getObject();

        String prefix = "resilience4j.circuitbreaker.instances." + OpenAiWebClient.CHAT_CIRCUIT_BREAKER + ".";
        assertThat(p.getProperty(prefix + "minimumNumberOfCalls")).isEqualTo("5");
        assertThat(p.getProperty(prefix + "failureRateThreshold")).isEqualTo("50");
        assertThat(p.getProperty(prefix + "slowCallDurationThreshold")).isEqualTo("60s");
        assertThat(p.getProperty("openai.stream.first-chunk-timeout-ms")).isEqualTo("10000");
    }
}
