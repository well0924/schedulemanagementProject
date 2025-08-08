package com.example.outbound.openai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
public class OpenAiWebClientConfig {

    @Bean(name = "openAiWebClientInternal")
    public WebClient openAiWebClient(WebClient.Builder builder,
                                     @Value("${openai.base-url}") String baseUrl) {
        log.info(baseUrl);
        return builder
                .baseUrl("https://api.openai.com/v1")
                .build();
    }
}
