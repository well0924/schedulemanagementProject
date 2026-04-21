package com.example.chatbot;

import com.example.inbound.schedules.ScheduleRecommendationCachePort;
import com.example.service.schedule.recommend.ScheduleRecommendCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
public class ChatHistoryServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ScheduleRecommendationCachePort cachePort;

    @InjectMocks
    private ScheduleRecommendCacheService chatHistoryService;

    @Test
    @DisplayName("대화 이력 초기화 - cachePort.clearChatHistory 호출 확인")
    void clearHistory_callsCachePort() {
        Long memberId = 1L;
        String expectedKey = "chat:history:" + memberId;

        // when
        chatHistoryService.clearChatHistory(memberId);

        // then
        // Service가 내부적으로 redisTemplate의 delete 메서드를 호출했는지 검증
        Mockito.verify(redisTemplate, Mockito.times(1)).delete(expectedKey);
    }
}
