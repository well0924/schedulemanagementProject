package com.example.auth;

import com.example.service.auth.RedisService;
import com.example.service.auth.jwt.JwtAuthenticationFilter;
import com.example.service.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 로그인·재발급 요청에 만료된 액세스 토큰이 헤더로 따라와도 필터에서 막히지 않아야 한다.
class JwtAuthenticationFilterTest {

    JwtTokenProvider jwtTokenProvider;
    RedisService redisService;
    JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        redisService = mock(RedisService.class);
        filter = new JwtAuthenticationFilter(jwtTokenProvider, redisService);
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(false); // 만료·위조 토큰
    }

    MockHttpServletRequest requestWithExpiredToken(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.addHeader("Authorization", "Bearer expired.access.token");
        return request;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/login", "/api/auth/reissue"})
    @DisplayName("로그인·재발급: 만료 토큰이 헤더에 있어도 컨트롤러까지 전달")
    void tokenFreePaths_passThrough(String uri) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithExpiredToken(uri), response, chain);

        assertThat(chain.getRequest()).as("다음 필터로 넘어가야 함").isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(jwtTokenProvider, never()).validateToken(anyString());
    }

    @Test
    @DisplayName("그 외 경로: 만료 토큰이면 기존대로 401")
    void otherPaths_rejectInvalidToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithExpiredToken("/api/schedule/1"), response, chain);

        assertThat(chain.getRequest()).as("다음 필터로 넘어가면 안 됨").isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("로그아웃: 토큰 검증 대상 유지 (만료 토큰이면 401)")
    void logout_stillValidated() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithExpiredToken("/api/auth/log-out"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }
}
