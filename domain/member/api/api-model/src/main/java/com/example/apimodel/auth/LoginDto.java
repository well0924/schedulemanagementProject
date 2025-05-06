package com.example.apimodel.auth;


import jakarta.validation.constraints.NotBlank;

public record LoginDto (
        @NotBlank(message = "아이디를 입력을 해주세요.")
        String userId,
        @NotBlank(message = "비밀번호를 입력을 해주세요.")
        String password) { }
