package com.example.auth;

import com.example.model.auth.CustomMemberDetails;
import com.example.model.member.MemberModel;
import com.example.security.config.ApiAuthorizationRules;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영 SecurityConfig와 같은 ApiAuthorizationRules를 적용해 경로별 인가 결과(200/401/403)를 검증한다.
// 컨트롤러는 모든 경로에 200을 돌려주는 대역을 써서 인가 규칙만 본다.
@SpringJUnitWebConfig(ApiAuthorizationRulesTest.TestConfig.class)
class ApiAuthorizationRulesTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class TestConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(ApiAuthorizationRules::apply)
                    .exceptionHandling(e -> e
                            .authenticationEntryPoint((req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                            .accessDeniedHandler((req, res, ex) -> res.sendError(HttpServletResponse.SC_FORBIDDEN)));
            return http.build();
        }

        @Bean
        AnyPathController anyPathController() {
            return new AnyPathController();
        }
    }

    @RestController
    static class AnyPathController {
        @RequestMapping("/**")
        public String ok() {
            return "ok";
        }
    }

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    static RequestPostProcessor member(long id) {
        return loginAs(id, "ROLE_USER");
    }

    static RequestPostProcessor admin(long id) {
        return loginAs(id, "ROLE_ADMIN");
    }

    static RequestPostProcessor loginAs(long id, String role) {
        CustomMemberDetails principal = CustomMemberDetails.builder()
                .memberModel(MemberModel.builder().id(id).userId("user" + id).build())
                .build();
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role))));
    }

    @Test
    @DisplayName("일정: 전체·단건·카테고리 조회는 비로그인도 허용")
    void schedule_publicReads() throws Exception {
        mockMvc.perform(get("/api/schedule/")).andExpect(status().isOk());
        mockMvc.perform(get("/api/schedule/10")).andExpect(status().isOk());
        mockMvc.perform(get("/api/schedule/category/회의")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("일정: 내 일정 목록은 비로그인 401 (기존엔 컨트롤러까지 가서 500)")
    void schedule_personalLists_requireLogin() throws Exception {
        mockMvc.perform(get("/api/schedule/today")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/schedule/user")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/schedule/status").param("status", "COMPLETE")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/schedule/today").with(member(1))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("일정: 생성·수정·상태변경·삭제는 로그인 필요")
    void schedule_writes_requireLogin() throws Exception {
        mockMvc.perform(post("/api/schedule/")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/schedule/10")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/schedule/status/10")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/schedule/bulk-delete")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/schedule/").with(member(1))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("일정: 삭제된 일정 전체 조회는 관리자만")
    void schedule_deleted_adminOnly() throws Exception {
        mockMvc.perform(get("/api/schedule/deleted")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/schedule/deleted").with(member(1))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/schedule/deleted").with(admin(99))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("첨부파일·카테고리: 조회 공개, 쓰기는 로그인 필요")
    void attachAndCategory() throws Exception {
        mockMvc.perform(get("/api/attach/1/presigned-download-url")).andExpect(status().isOk());
        mockMvc.perform(post("/api/attach/presigned-urls")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/attach/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/attach/1").with(member(1))).andExpect(status().isOk());

        mockMvc.perform(get("/api/category/")).andExpect(status().isOk());
        mockMvc.perform(post("/api/category/")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/category/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("알림 목록: 본인 번호만 허용, 남의 번호는 403, 비로그인 401, 관리자 허용")
    void notice_selfOnly() throws Exception {
        mockMvc.perform(get("/api/notice/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notice/1").with(member(1))).andExpect(status().isOk());
        mockMvc.perform(get("/api/notice/2").with(member(1))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/notice/unread/2").with(member(1))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/notice/unread/1").with(member(1))).andExpect(status().isOk());
        mockMvc.perform(get("/api/notice/2").with(admin(99))).andExpect(status().isOk());
        mockMvc.perform(patch("/api/notice/5/read").with(member(1))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("웹 푸시·알림 설정: memberId 파라미터/경로가 본인일 때만 허용")
    void push_and_setting_selfOnly() throws Exception {
        mockMvc.perform(get("/api/push/active").param("memberId", "1").with(member(1))).andExpect(status().isOk());
        mockMvc.perform(get("/api/push/active").param("memberId", "2").with(member(1))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/push/unsubscribeAll").param("memberId", "2").with(member(1))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/push/unsubscribe").param("memberId", "1").param("endpoint", "e").with(member(1))).andExpect(status().isOk());
        mockMvc.perform(post("/api/push/unsubscribeAll")).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/notification-setting/me/reset/2").with(member(1))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/notification-setting/me/reset/1").with(member(1))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증: 로그인·재발급·회원가입은 공개, 회원 조회는 로그인 필요")
    void authAndMember() throws Exception {
        mockMvc.perform(post("/api/auth/login")).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/reissue")).andExpect(status().isOk());
        mockMvc.perform(post("/api/member/")).andExpect(status().isOk());
        mockMvc.perform(get("/api/member/1")).andExpect(status().isUnauthorized());
    }
}
