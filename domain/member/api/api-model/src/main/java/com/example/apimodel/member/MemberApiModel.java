package com.example.apimodel.member;

import com.example.enumerate.member.Roles;
import lombok.Builder;

import java.time.LocalDateTime;

public class MemberApiModel {

    public record CreateRequest(
            String userId,
            String password,
            String userEmail,
            String userName,
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
            String password,
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
