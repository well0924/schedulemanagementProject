package com.example.inbound.consumer.slack;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotifier {

    private final WebClient webClient = WebClient.create();

    @Value("${slack.webhook-url}")
    private String webhookUrl;

    public void send(String title, String message) {
        String payload = String.format("{\"text\":\"*%s*\\n%s\"}", title, message);

        webClient.post()
                .uri(webhookUrl)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.error("Slack 전송 실패", e))
                .subscribe(response -> log.info("Slack 응답: {}", response));
    }
}
