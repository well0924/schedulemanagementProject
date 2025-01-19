package com.example.security.jwt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenDto implements Serializable {
    private String grantType;
    private String accessToken;
    private String refreshToken;
    private Long refreshTokenExpiredTime;
}
