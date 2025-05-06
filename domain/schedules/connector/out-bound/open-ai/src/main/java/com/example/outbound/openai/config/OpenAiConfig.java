package com.example.outbound.openai.config;

import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class OpenAiConfig {

    @Value("${openai.secret-key}")
    private String openAiApiKey;

    @Bean
    public RequestInterceptor openAiRequestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("Authorization", "Bearer " + openAiApiKey);
            requestTemplate.header("Content-Type", "application/json");
        };
    }

    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(
                1000,    // initial interval (ms)
                TimeUnit.SECONDS.toMillis(1), // max interval
                3        // max attempts
        );
    }
}
