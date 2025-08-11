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
                                     @Value("${openai.base-url}") String baseUrl) {
        log.info(baseUrl);

        // HttpClient readtimeout, connection timeout 설정
        HttpClient httpClient = HttpClient.create()
                // 1) TCP 연결 시도 제한
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                // 2) 응답 대기 제한 (서버가 첫 바이트 주기까지)
                .responseTimeout(Duration.ofSeconds(30))
                // 3) 소켓 레벨 read/write 타임아웃
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS) )
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS))
                );

        return builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
