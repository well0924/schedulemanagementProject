package com.example.interfaces.auth;

import com.example.apimodel.auth.LoginDto;
import com.example.apimodel.auth.RequestRefreshTokenDto;
import com.example.apimodel.auth.TokenResponse;

public interface AuthInterface {

    //로그인
    public TokenResponse login(LoginDto dto);
    //로그아웃
    public void logout(String accessToken);
    //토큰 재발급
    public TokenResponse tokenReissue(RequestRefreshTokenDto refreshTokenDto);
    //회원 현재 번호
    public Long currentUserId(String username);
}
