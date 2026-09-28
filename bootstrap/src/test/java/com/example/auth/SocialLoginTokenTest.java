package com.example.auth;

import com.example.model.auth.CustomMemberDetails;
import com.example.model.member.MemberModel;
import com.example.outbound.auth.AuthOutConnector;
import com.example.service.auth.RedisService;
import com.example.service.auth.jwt.JwtTokenProvider;
import com.example.service.auth.oauth2.OAuth2AuthenticationFailureHandler;
import com.example.service.auth.oauth2.OAuth2AuthenticationSuccessHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// 소셜 로그인 성공 시 발급 토큰·Redis 저장·프론트 리다이렉트 검증
class SocialLoginTokenTest {

    static final String SECRET = Base64.getEncoder().encodeToString(
            "social-login-test-secret-key-0123456789abcdef".getBytes());

    // 구글 로그인 결과와 같은 형태의 인증 객체 (attributeKey = sub)
    static OAuth2AuthenticationToken googleLogin() {
        CustomMemberDetails principal = CustomMemberDetails.builder()
                .memberModel(MemberModel.builder().id(7L).userId("social_1790000000000").build())
                .attributes(Map.of("sub", "google-sub-123", "email", "tester@gmail.com"))
                .attributeKey("sub")
                .build();
        return new OAuth2AuthenticationToken(principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "google");
    }

    @Test
    @DisplayName("발급 토큰의 subject는 구글 sub가 아니라 회원 아이디(user_id)")
    void tokenSubject_isUserId() {
        OAuth2AuthenticationToken authentication = googleLogin();
        System.out.println("PROBE authentication.getName() = " + authentication.getName());

        JwtTokenProvider provider = new JwtTokenProvider(SECRET, mock(AuthOutConnector.class));
        String accessToken = provider.generateToken(authentication).getAccessToken();

        // JWT payload(가운데 부분)를 직접 디코딩해 subject 확인
        String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        assertThat(payload).contains("\"sub\":\"social_1790000000000\"");
    }

    @Test
    @DisplayName("성공: Refresh Token을 회원 아이디로 Redis에 저장하고, 두 토큰을 # 뒤에 담아 프론트로 보낸다")
    void success_savesRefreshAndRedirectsWithFragment() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, mock(AuthOutConnector.class));
        RedisService redisService = mock(RedisService.class);
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(provider, redisService);
        ReflectionTestUtils.setField(handler, "authorizedRedirectUri", "http://localhost:3000/auth/callback");

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, googleLogin());

        verify(redisService).saveRefreshToken(eq("social_1790000000000"), anyString());
        String redirect = response.getRedirectedUrl();
        assertThat(redirect).startsWith("http://localhost:3000/auth/callback#accessToken=");
        assertThat(redirect).contains("&refreshToken=");
        assertThat(redirect).doesNotContain("?");
    }

    @Test
    @DisplayName("실패: API 서버 경로가 아니라 프론트 콜백으로 error와 함께 보낸다")
    void failure_redirectsToFrontWithError() throws Exception {
        OAuth2AuthenticationFailureHandler handler = new OAuth2AuthenticationFailureHandler();
        ReflectionTestUtils.setField(handler, "authorizedRedirectUri", "http://localhost:3000/auth/callback");

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "access denied"));

        assertThat(response.getRedirectedUrl()).startsWith("http://localhost:3000/auth/callback?error=");
    }
}
