package com.example.apimodel.auth;

public record RequestRefreshTokenDto(
        String accessToken,
        String refreshToken
) { }
