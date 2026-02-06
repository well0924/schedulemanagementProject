package com.example.apimodel.member;

import com.example.enumerate.member.Roles;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.LocalDateTime;

public class MemberApiModel {

    public record CreateRequest(
            @NotBlank(message = "아이디를 입력해주세요.")
            String userId,
            @NotBlank(message = "비밀번호를 입력해주세요.")
            String password,
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            @NotBlank(message = "이메일을 입력해주세요.")
            String userEmail,
            @NotBlank(message = "이름을 입력해주세요.")
            String userName,
            @NotBlank(message = "전화번호를 입력해주세요.")
            String userPhone
    ) {}

    public record UpdateRequest(
            String userId,
            String userEmail,
            String userName,
            String userPhone
    ) {}

    @Builder
    public record MemberResponse(
            Long id,
            String userId,
            String userEmail,
            String userPhone,
            String userName,
            Roles roles,
            String createdBy,
            String updatedBy,
            LocalDateTime createdTime,
            LocalDateTime updatedTime
    ) {

    }
}
