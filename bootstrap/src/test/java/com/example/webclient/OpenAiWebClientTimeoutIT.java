package com.example.webclient;

import com.example.outbound.openai.config.OpenAiWebClient;
import com.example.outbound.openai.config.OpenAiWebClientConfig;
import com.example.outbound.openai.dto.OpenAiRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import reactor.netty.http.client.HttpClient;

import java.net.ConnectException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;


@SpringBootTest(classes = { OpenAiWebClientConfig.class, OpenAiWebClient.class ,TestSupportConfig.class})
public class OpenAiWebClientTimeoutIT {

    static WireMockServer wm;

    @BeforeAll
    static void beforeAll() {
        wm = new WireMockServer(8089);
        wm.start();
        configureFor("localhost", 8089);
    }

    @AfterAll
    static void afterAll() {
        if (wm != null && wm.isRunning()) {
            wm.stop();
        }
    }

    // 테스트 전용 타임아웃(짧게)
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("openai.base-url", () -> "http://localhost:8089");
        r.add("openai.http.connect-timeout-ms", () -> 200);
        r.add("openai.http.response-timeout-ms", () -> 300);
        r.add("openai.http.read-timeout-ms", () -> 300);
        r.add("openai.http.write-timeout-ms", () -> 300);
        r.add("openai.secret-key", () -> "dummy");
    }

    @Autowired
    OpenAiWebClient client;

    @Autowired
    OpenAiWebClientConfig config;

    @Test
    @DisplayName("서버 응답 지연 1s → response/read timeout(300ms) 발생")
    void readOrResponseTimeoutShouldThrow() {
        wm.stubFor(post(WireMock.urlEqualTo("/chat/completions"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withFixedDelay(1000) // 300ms보다 길게 -> timeout 유발
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"x\",\"object\":\"chat.completion\",\"choices\":[]}")));

        OpenAiRequest req = new OpenAiRequest();

        StepVerifier.create(client.getChatCompletion(req))
                .expectErrorSatisfies(ex ->
                        Assertions.assertTrue(
                                hasCause(ex, ReadTimeoutException.class, TimeoutException.class),
                                "Read/Response timeout이어야 함. actual=" + ex
                        )
                )
                .verify();
    }

    @Test
    @DisplayName("비라우팅 IP 접속 시 ConnectTimeout/TimeoutException 발생")
    void connectTimeoutShouldThrow() {
        // 연결이 거의 항상 타임아웃나는 TEST-NET-1 주소
        String baseUrl = "http://192.0.2.1:59999";

        HttpClient hc = HttpClient
                .create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 200)
                .responseTimeout(Duration.ofMillis(500))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(500, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(500, TimeUnit.MILLISECONDS)));

        WebClient wc = WebClient
                .builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(hc))
                .build();

        StepVerifier.create(
                        wc.post()
                                .uri("/chat/completions")
                                .retrieve()
                                .bodyToMono(String.class)
                )
                .expectErrorSatisfies(ex ->
                        Assertions.assertTrue(
                                hasCause(ex,
                                        ConnectTimeoutException.class,
                                        ConnectException.class,
                                        TimeoutException.class,
                                        UnresolvedAddressException.class
                                ),
                                "connect 계열 타임아웃이어야 함. actual=" + ex
                        )
                )
                .verify();
    }

    // 원인 체인에 특정 예외가 있는지가를 검사
    private static boolean hasCause(Throwable t, Class<?>... types) {
        Throwable cur = t;
        while (cur != null) {
            for (Class<?> type : types) {
                if (type.isInstance(cur)) return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

}
