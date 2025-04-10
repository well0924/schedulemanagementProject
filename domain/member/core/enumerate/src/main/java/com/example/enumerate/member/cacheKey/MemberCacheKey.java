package com.example.enumerate.member.cacheKey;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MemberCacheKey {
    //회원
    public static final String USER = "user";
    //로그인 관련
    public static final String REFRESH_TOKEN = "refreshToken";
    public static final String BLACKLIST = "blackList";
}
