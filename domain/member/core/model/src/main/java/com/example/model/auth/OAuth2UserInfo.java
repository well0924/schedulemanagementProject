package com.example.model.auth;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
public class OAuth2UserInfo {

    private final String name;
    private final String email;
    private final String profile;

    @Builder
    public OAuth2UserInfo(String name, String email, String profile) {
        this.name = name;
        this.email = email;
        this.profile = profile;
    }

    public static OAuth2UserInfo of(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "google" -> ofGoogle(attributes);
            default -> throw new IllegalArgumentException("Unsupported OAuth2 provider: " + registrationId);
        };
    }

    private static OAuth2UserInfo ofGoogle(Map<String, Object> attributes) {
        return OAuth2UserInfo.builder()
                .name((String) attributes.get("name"))
                .email((String) attributes.get("email"))
                .profile((String) attributes.get("picture"))
                .build();
    }


}
