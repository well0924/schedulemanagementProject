package com.example.service.schedule.recommend;

import com.example.inbound.schedules.ScheduleRecommendationCachePort;
import com.example.outbound.openai.dto.ChatMessage;
import com.example.redis.config.cachekey.CacheKey;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ScheduleRecommendCacheService implements ScheduleRecommendationCachePort {

    private final RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper;

    private static final String HISTORY_PREFIX = CacheKey.CHAT_HISTORY.getKey() + ":";

    private static final int MAX_HISTORY = 10;

    private static final Duration CHAT_TTL = Duration.ofHours(2);
    private static final Duration PATTERN_TTL = Duration.ofDays(7);

    private String getHistoryKey(Long memberId) {
        return HISTORY_PREFIX + memberId;
    }

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

    // 대화 메시지 추가
    // race condition 의 문제가 있음.....
    public void appendChatMessage(Long memberId, ChatMessage message) {
        String key = getHistoryKey(memberId);
        redisTemplate.opsForList().rightPush(key, message);
        redisTemplate.opsForList().trim(key, -MAX_HISTORY, -1); // 최근 N개만 유지
        redisTemplate.expire(key, CHAT_TTL);
    }

    // 대화 이력 전체 조회
    public List<ChatMessage> getChatHistory(Long memberId) {
        String key = getHistoryKey(memberId);
        List<Object> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) return List.of();

        return raw.stream()
                .map(obj -> objectMapper.convertValue(obj, ChatMessage.class))
                .collect(Collectors.toList());
    }

    public void clearChatHistory(Long memberId) {
        redisTemplate.delete(getHistoryKey(memberId));
    }

    @Override
    public void increment(String key, String hashKey, long delta) {
        redisTemplate.opsForHash().increment(key, hashKey, delta);
        redisTemplate.expire(key, PATTERN_TTL);
    }
}
