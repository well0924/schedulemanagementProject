package com.example.service.auth.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import java.io.IOException;

@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    // 성공과 같은 프론트 콜백 페이지로 돌려보내고, 프론트가 error를 보고 실패 안내를 한다.
    // (기존 /auth/failure는 API 서버 경로라 운영에서 404)
    @Value("${app.oauth2.authorized-redirect-uri:http://localhost:3000/auth/callback}")
    private String authorizedRedirectUri;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        String redirectUrl = UriComponentsBuilder.fromUriString(authorizedRedirectUri)
                .queryParam("error", exception.getLocalizedMessage())
                .encode()
                .build().toUriString();

        response.sendRedirect(redirectUrl);
    }
}
