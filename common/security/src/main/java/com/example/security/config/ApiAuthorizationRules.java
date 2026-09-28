package com.example.security.config;

import com.example.model.auth.CustomMemberDetails;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Function;

/**
 * API 경로별 인가 규칙.
 * SecurityConfig와 테스트가 같은 규칙을 쓰도록 분리했다. 규칙은 위에서부터 먼저 매칭되는 것이 적용된다.
 */
public final class ApiAuthorizationRules {

    private ApiAuthorizationRules() {
    }

    public static void apply(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
        registry
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // 프리플라이트 전부 허용
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/member/**").permitAll() // 회원가입만 공개
                .requestMatchers("/api/member/**").authenticated() // 조회/수정/삭제는 로그인 필요

                // 알림: 요청 경로·파라미터의 회원 번호가 로그인한 본인일 때만 허용 (관리자는 예외)
                .requestMatchers(HttpMethod.GET, "/api/notice/{userId}", "/api/notice/unread/{userId}")
                .access(selfOrAdmin(ctx -> ctx.getVariables().get("userId")))
                .requestMatchers("/api/notice/**").authenticated()
                .requestMatchers("/api/push/active", "/api/push/unsubscribe", "/api/push/unsubscribeAll")
                .access(selfOrAdmin(ctx -> ctx.getRequest().getParameter("memberId")))
                .requestMatchers("/api/notification-setting/me/reset/{userId}")
                .access(selfOrAdmin(ctx -> ctx.getVariables().get("userId")))

                // 카테고리·첨부파일: 조회는 공개, 생성·수정·삭제는 로그인 필요
                .requestMatchers(HttpMethod.GET, "/api/category/**").permitAll()
                .requestMatchers("/api/category/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/attach/**").permitAll()
                .requestMatchers("/api/attach/**").authenticated()

                // 일정: 전체·단건·카테고리별 조회는 공개, 내 일정 목록과 쓰기는 로그인 필요
                .requestMatchers("/api/schedule/deleted").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/schedule/user", "/api/schedule/status", "/api/schedule/today").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/schedule/**").permitAll()
                .requestMatchers("/api/schedule/**").authenticated()

                .requestMatchers("/api/actuator/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/ws/**", "/topic/**").permitAll()
                .anyRequest()
                .authenticated();
    }

    /**
     * 요청에서 꺼낸 회원 번호가 로그인한 회원 번호와 같거나, 관리자면 허용.
     * 비로그인이면 거부되어 인증 진입점(401)으로 간다.
     */
    static AuthorizationManager<RequestAuthorizationContext> selfOrAdmin(
            Function<RequestAuthorizationContext, String> requestedMemberId) {
        return (authenticationSupplier, context) -> {
            Authentication authentication = authenticationSupplier.get();
            if (authentication == null || !(authentication.getPrincipal() instanceof CustomMemberDetails member)) {
                return new AuthorizationDecision(false);
            }
            boolean admin = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (admin) {
                return new AuthorizationDecision(true);
            }
            String requested = requestedMemberId.apply(context);
            return new AuthorizationDecision(
                    requested != null && requested.equals(String.valueOf(member.getMemberModel().getId())));
        };
    }
}
