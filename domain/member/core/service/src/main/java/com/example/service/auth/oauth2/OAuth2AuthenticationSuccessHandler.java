package com.example.service.auth.oauth2;

import com.example.model.auth.CustomMemberDetails;
import com.example.service.auth.RedisService;
import com.example.service.auth.jwt.JwtTokenProvider;
import com.example.service.auth.jwt.TokenDto;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    private final RedisService redisService;

    // 로그인 후 돌아갈 프론트 주소 (프론트 배포 후 운영 주소로 설정)
    @Value("${app.oauth2.authorized-redirect-uri:http://localhost:3000/auth/callback}")
    private String authorizedRedirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        TokenDto dto = jwtTokenProvider.generateToken(authentication);

        // 일반 로그인과 같이 Refresh Token을 Redis에 저장해야 재발급이 동작한다.
        // 키는 토큰 subject와 같은 회원 아이디(user_id)
        String userId = ((CustomMemberDetails) authentication.getPrincipal()).getUsername();
        redisService.saveRefreshToken(userId, dto.getRefreshToken());

        // 토큰은 쿼리스트링이 아닌 URL 프래그먼트(#)로 전달해 서버 액세스 로그·Referer 헤더에 남지 않도록 한다.
        // 프론트는 location.hash에서 두 토큰을 읽어 일반 로그인과 같은 방식으로 저장한다.
        String redirectUrl = UriComponentsBuilder.fromUriString(authorizedRedirectUri)
                .fragment("accessToken=" + dto.getAccessToken() + "&refreshToken=" + dto.getRefreshToken())
                .build().toUriString();

        response.sendRedirect(redirectUrl);
    }
}
