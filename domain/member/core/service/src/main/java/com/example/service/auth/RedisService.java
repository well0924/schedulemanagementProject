package com.example.service.auth;

import com.example.enumerate.member.cacheKey.MemberCacheKey;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class RedisService {

    @Cacheable(value = MemberCacheKey.REFRESH_TOKEN, key = "#root.args[0]", unless = "#result == null")
    public String findRefreshToken(String userId) {
        return null; // 캐시에 없으면 null 반환
    }

    @CachePut(value = MemberCacheKey.REFRESH_TOKEN, key = "#root.args[0]")
    public String saveRefreshToken(String userId, String refreshToken) {
        return refreshToken; // 캐시에 저장
    }

    @CacheEvict(value = MemberCacheKey.REFRESH_TOKEN, key = "#root.args[0]")
    public void deleteRefreshToken(String userId) {
        // 캐시에서 Refresh Token 삭제
    }

    @CachePut(value = MemberCacheKey.BLACKLIST, key = "#root.args[0]")
    public String saveBlacklistToken(String accessToken, String status, long expiration) {
        return status; // 블랙리스트에 저장
    }
}
