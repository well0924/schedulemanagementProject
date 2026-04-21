package com.example.chatbot;

import com.example.events.spring.ChatCompletedEvent;
import com.example.inbound.consumer.chatbot.PatternAnalysisConsume;
import com.example.inbound.schedules.ScheduleRecommendationCachePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class PatternAnalysisConsumerTest {

    @Mock
    private ScheduleRecommendationCachePort cachePort;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private PatternAnalysisConsume consumer;

    @Test
    @DisplayName("오전 키워드 포함 - morning 패턴 저장")
    void handle_morningKeyword_saveMorningPattern() {
        // given
        ChatCompletedEvent event = ChatCompletedEvent.builder()
                .memberId(1L)
                .userMessage("오전에 운동 일정 잡아줘")
                .assistantResponse("답변")
                .createdAt(LocalDateTime.now())
                .build();

        // when
        consumer.handle(event, ack);

        // then
        verify(cachePort).set(
                eq("chat:pattern:1:time"),
                eq("morning"),
                eq(Duration.ofDays(7))
        );
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("저녁 키워드 포함 - afternoon 패턴 저장")
    void handle_eveningKeyword_saveAfternoonPattern() {
        // given
        ChatCompletedEvent event = ChatCompletedEvent.builder()
                .memberId(1L)
                .userMessage("저녁 일정 추천해줘")
                .assistantResponse("답변")
                .createdAt(LocalDateTime.now())
                .build();

        // when
        consumer.handle(event, ack);

        // then
        verify(cachePort).set(
                eq("chat:pattern:1:time"),
                eq("afternoon"),
                eq(Duration.ofDays(7))
        );
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("관련 키워드 없을 때 - 패턴 저장 안 됨")
    void handle_noKeyword_noPatternSaved() {
        // given
        ChatCompletedEvent event = ChatCompletedEvent.builder()
                .memberId(1L)
                .userMessage("일정 보여줘")
                .assistantResponse("답변")
                .createdAt(LocalDateTime.now())
                .build();

        // when
        consumer.handle(event, ack);

        // then
        verify(cachePort, times(0)).set(any(), any(), any());
        verify(ack).acknowledge();
    }
}
