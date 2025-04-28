package com.example.service.auth;

import com.example.enumerate.member.cacheKey.MemberCacheKey;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class RedisService {

    private final StringRedisTemplate redisTemplate;
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    // Refresh Token 저장
    public void saveRefreshToken(String userId, String refreshToken) {
        String key = generateRefreshTokenKey(userId);
        redisTemplate.opsForValue().set(key, refreshToken, REFRESH_TOKEN_TTL);
    }

    // Refresh Token 조회
    public String findRefreshToken(String userId) {
        String key = generateRefreshTokenKey(userId);
        return redisTemplate.opsForValue().get(key);
    }

    // Refresh Token 삭제
    public void deleteRefreshToken(String userId) {
        String key = generateRefreshTokenKey(userId);
        redisTemplate.delete(key);
    }

    // AccessToken 블랙리스트 등록
    public void saveBlacklistToken(String accessToken, String status, long expirationMillis) {
        String key = generateBlacklistKey(accessToken);
        redisTemplate.opsForValue().set(key, status, Duration.ofMillis(expirationMillis));
    }

    // 블랙리스트 키 생성
    private String generateBlacklistKey(String accessToken) {
        return "blacklist:" + accessToken;
    }

    // RefreshToken 키 생성
    private String generateRefreshTokenKey(String userId) {
        return "refreshToken:" + userId;
    }
}
