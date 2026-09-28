package com.example.exception;

import com.example.category.dto.CategoryErrorCode;
import com.example.category.exception.CategoryCustomException;
import com.example.controller.schedule.ExceptionRoutingScheduleController;
import com.example.exception.dto.ErrorCode;
import com.example.exception.dto.MemberErrorCode;
import com.example.exception.exception.MemberCustomException;
import com.example.exception.global.CustomGlobalExceptionHandler;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomGlobalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 예외 Advice 라우팅 검증: 어떤 예외가 어느 Advice로 가서 어떤 상태코드가 나오는지
class ExceptionAdviceRoutingTest {

    MockMvc mockMvc;

    // 일정 외 도메인 컨트롤러 역할 (패키지가 com.example.controller.schedule 밖)
    @RestController
    @RequestMapping("/test/other")
    static class OtherDomainController {

        @GetMapping("/member-exception")
        public void memberException() {
            throw new MemberCustomException(MemberErrorCode.NOT_FIND_USERID);
        }

        @GetMapping("/category-exception")
        public void categoryException() {
            throw new CategoryCustomException(CategoryErrorCode.INVALID_PARENT_CATEGORY);
        }

        @GetMapping("/db-violation")
        public void dbViolation() {
            throw new DataIntegrityViolationException("uk_member_user_id");
        }

        @PostMapping("/body")
        public void body(@RequestBody Map<String, Object> body) {
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OtherDomainController(), new ExceptionRoutingScheduleController())
                .setControllerAdvice(new ScheduleCustomGlobalException(), new CustomGlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("회원 예외 → 전역 핸들러가 MemberErrorCode의 상태코드·코드로 응답")
    void memberException_routedToGlobal() throws Exception {
        mockMvc.perform(get("/test/other/member-exception"))
                .andExpect(status().is(MemberErrorCode.NOT_FIND_USERID.getHttpStatus().value()))
                .andExpect(jsonPath("$.errorCode").value(MemberErrorCode.NOT_FIND_USERID.getCode()))
                .andExpect(jsonPath("$.message").value(MemberErrorCode.NOT_FIND_USERID.getMessage()));
    }

    @Test
    @DisplayName("카테고리 예외 → 전역 핸들러가 CategoryErrorCode의 상태코드·코드로 응답")
    void categoryException_routedToGlobal() throws Exception {
        mockMvc.perform(get("/test/other/category-exception"))
                .andExpect(status().is(CategoryErrorCode.INVALID_PARENT_CATEGORY.getHttpStatus().value()))
                .andExpect(jsonPath("$.errorCode").value(CategoryErrorCode.INVALID_PARENT_CATEGORY.getCode()));
    }

    @Test
    @DisplayName("일정 컨트롤러에서 던진 카테고리 예외도 도메인 응답 그대로 (일정 핸들러를 거쳐 전역으로)")
    void categoryException_fromScheduleController() throws Exception {
        mockMvc.perform(get("/test/schedule/category-exception"))
                .andExpect(status().is(CategoryErrorCode.INVALID_PARENT_CATEGORY.getHttpStatus().value()))
                .andExpect(jsonPath("$.errorCode").value(CategoryErrorCode.INVALID_PARENT_CATEGORY.getCode()));
    }

    @Test
    @DisplayName("일정 예외 → 일정 핸들러가 ScheduleErrorCode로 응답")
    void scheduleException_routedToSchedule() throws Exception {
        mockMvc.perform(get("/test/schedule/schedule-exception"))
                .andExpect(status().is(ScheduleErrorCode.SCHEDULE_NOT_FOUND.getHttpStatus().value()))
                .andExpect(jsonPath("$.errorCode").value(ScheduleErrorCode.SCHEDULE_NOT_FOUND.getStatus()));
    }

    @Test
    @DisplayName("일정 소유자가 아닌 요청 → 500이 아니라 403")
    void notScheduleOwner_is403() throws Exception {
        mockMvc.perform(get("/test/schedule/not-owner"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ScheduleErrorCode.NOT_SCHEDULE_OWNER.getStatus()));
    }

    @Test
    @DisplayName("일정 컨트롤러의 DB 제약 위반 → 일정 충돌(409, 40024)")
    void dbViolation_fromSchedule() throws Exception {
        mockMvc.perform(get("/test/schedule/db-violation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(40024));
    }

    @Test
    @DisplayName("다른 도메인의 DB 제약 위반 → 일정 충돌 메시지가 아니라 전역 DB_ERROR(409)")
    void dbViolation_fromOtherDomain() throws Exception {
        mockMvc.perform(get("/test/other/db-violation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.DB_ERROR.getStatus()));
    }

    @Test
    @DisplayName("잘못된 JSON 바디 → 500이 아니라 400")
    void malformedBody_is400() throws Exception {
        mockMvc.perform(post("/test/other/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_PARAMETER.getStatus()));
    }
}
