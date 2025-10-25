package com.example.inbound.auth;

import com.example.apimodel.auth.LoginDto;
import com.example.apimodel.auth.RequestRefreshTokenDto;
import com.example.apimodel.auth.TokenResponse;
import com.example.interfaces.auth.AuthInterface;
import com.example.service.auth.AuthService;
import com.example.service.auth.jwt.TokenDto;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class AuthInConnector implements AuthInterface {

    private final AuthService authService;

    @Override
    public TokenResponse login(LoginDto dto) {
        TokenDto loginResult = authService.login(dto.userId(),dto.password());
        return tokenResponse(loginResult);
    }

    @Override
    public void logout(String accessToken) {
        authService.logout(accessToken);
    }

    @Override
    public TokenResponse tokenReissue(RequestRefreshTokenDto refreshTokenDto) {
        TokenDto reissueResult = authService.tokenReissue(refreshTokenDto.accessToken(), refreshTokenDto.refreshToken());
        return tokenResponse(reissueResult);
    }

    @Override
    public Long currentUserId(String username) {
        return authService.currentUserId(username);
    }


    public TokenResponse tokenResponse(TokenDto dto) {
        return TokenResponse
                .builder()
                .grantType(dto.getGrantType())
                .accessToken(dto.getAccessToken())
                .refreshToken(dto.getRefreshToken())
                .refreshTokenExpiredTime(dto.getRefreshTokenExpiredTime())
                .build();
    }
}
