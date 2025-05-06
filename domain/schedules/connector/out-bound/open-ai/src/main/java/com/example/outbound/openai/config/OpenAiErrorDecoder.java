package com.example.outbound.openai.config;

import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();

        if (status == 429 || status == 500 || status == 503 || status == 504) {
            return new RetryableException(
                    response.status(),
                    "Retryable error from OpenAI API",
                    response.request().httpMethod(),
                    (Long) null,
                    response.request()
            );
        }

        return defaultDecoder.decode(methodKey, response);
    }
}
