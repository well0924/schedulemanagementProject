package com.example.outbound.openai.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class OpenAiWebClientConfig {

    @Bean(name = "openAiWebClientInternal")
    public WebClient openAiWebClient(WebClient.Builder builder,
                                     @Value("${openai.base-url}") String baseUrl,
                                     @Value("${openai.http.connect-timeout-ms:5000}") int connectTimeoutMs,
                                     @Value("${openai.http.response-timeout-ms:30000}") long responseTimeoutMs,
                                     @Value("${openai.http.read-timeout-ms:30000}") long readTimeoutMs,
                                     @Value("${openai.http.write-timeout-ms:30000}") long writeTimeoutMs) {
        log.info(baseUrl);

        // HttpClient readtimeout, connection timeout 설정
        HttpClient httpClient = HttpClient.create()
                // 1) TCP 연결 시도 제한
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,connectTimeoutMs)
                // 2) 응답 대기 제한 (서버가 첫 바이트 주기까지)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs))
                // 3) 소켓 레벨 read/write 타임아웃
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.SECONDS) )
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeoutMs, TimeUnit.SECONDS))
                );

        return builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
