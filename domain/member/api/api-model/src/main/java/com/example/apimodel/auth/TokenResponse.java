package com.example.apimodel.auth;

import lombok.Builder;

@Builder
public record TokenResponse(
        String grantType,
        String accessToken,
        String refreshToken,
        Long refreshTokenExpiredTime) {
}
