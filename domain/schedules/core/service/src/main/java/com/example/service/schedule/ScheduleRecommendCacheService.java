package com.example.service.schedule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ScheduleRecommendCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper;

    public <T> void set(String key, T value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public <T> Optional<T> get(String key, TypeReference<T> typeRef) {
        Object result = redisTemplate.opsForValue().get(key);
        if (result == null) return Optional.empty();

        try {
            // Object → JSON → 타입 변환 (예: List<SchedulesModel>)
            T deserialized = objectMapper.convertValue(result, typeRef);
            return Optional.of(deserialized);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
