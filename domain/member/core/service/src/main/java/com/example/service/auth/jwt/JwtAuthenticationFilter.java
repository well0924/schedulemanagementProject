package com.example.service.auth.jwt;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;
import com.example.service.auth.RedisService;

import java.io.IOException;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends GenericFilterBean {

    private final  JwtTokenProvider jwtTokenProvider;

    private final RedisService redisService;

    private static final Set<String> TOKEN_FREE_PATHS = Set.of("/api/auth/login", "/api/auth/reissue");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String path = httpRequest.getRequestURI();
        if (path.startsWith("/ws") || path.startsWith("/topic")) {
            chain.doFilter(request, response);
            return;
        }

        // 로그인·토큰 재발급은 액세스 토큰 없이 동작해야 한다.
        // 만료된 토큰이 Authorization 헤더에 남아 있어도 여기서 401을 내면 재발급 자체가 막힌다.
        if (TOKEN_FREE_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        String token = resolveToken((HttpServletRequest) request);

        if (token != null) {
            if (jwtTokenProvider.validateToken(token)) {
                if (redisService.isBlacklisted(token)) {
                    HttpServletResponse blacklistResponse = (HttpServletResponse) response;
                    blacklistResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "로그아웃된 토큰입니다.");
                    return;
                }
                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                log.debug("JwtAuthenticationFilter - 인증 성공: {}", authentication.getName());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                MDC.put("username", authentication.getName()); // 또는 getMemberModel().getUserId() 도 가능

            } else {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT token");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
