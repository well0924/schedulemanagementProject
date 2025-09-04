package com.example.apiclient.config.webpush;

import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Security;

@Configuration
public class WebPushConfig {

    @Value("${webpush.vapid.public}")
    private String publicKey;

    @Value("${webpush.vapid.private}")
    private String privateKey;

    @Value("${webpush.subject}")
    private String subject;

    @PostConstruct
    public void setupProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Bean
    public PushService pushService () throws Exception {
        return new PushService(publicKey, privateKey, subject);
    }
}
