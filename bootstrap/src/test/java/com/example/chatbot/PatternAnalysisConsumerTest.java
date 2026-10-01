package com.example.chatbot;

import com.example.events.process.ProcessedEventService;
import com.example.events.spring.ChatCompletedEvent;
import com.example.inbound.consumer.chatbot.PatternAnalysisConsume;
import com.example.inbound.schedules.ScheduleRecommendationCachePort;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.interfaces.category.CategoryRepositoryPort;
import com.example.model.category.CategoryModel;
import com.example.model.schedules.CategoryFrequency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PatternAnalysisConsume.handle()은 두 가지를 한다:
 * 1) analyzeTimePreference: 메시지 내용과 무관하게 event.createdAt()의 "시각"으로
 *    morning/afternoon을 나눠 "pattern:time:{memberId}" 키에 increment
 * 2) analyzeMessageContext: 이 회원이 실제 쓰는 카테고리 이름이 메시지에 등장하면
 *    "pattern:interest:{memberId}" 키에 해당 카테고리 이름으로 increment
 */
@ExtendWith(MockitoExtension.class)
public class PatternAnalysisConsumerTest {

    @Mock
    private ScheduleRecommendationCachePort cachePort;

    @Mock
    private ScheduleRepositoryPort scheduleRepositoryPort;

    @Mock
    private CategoryRepositoryPort categoryRepositoryPort;

    @Mock
    private Acknowledgment ack;

    // handle()이 제일 먼저 호출하는 의존성인데 기존 테스트엔 없어서 NPE가 나던 부분
    @Mock
    private ProcessedEventService processedEventService;

    @InjectMocks
    private PatternAnalysisConsume consumer;

    @BeforeEach
    void setUp() {
        when(processedEventService.isAlreadyProcessed(anyString(), anyString())).thenReturn(false);
    }

    @Test
    @DisplayName("오전 시간대 채팅 - time 패턴에 morning 누적")
    void handle_morningHour_incrementsMorningPattern() {
        // given
        when(scheduleRepositoryPort.countByCategoryForMember(1L)).thenReturn(List.of());

        ChatCompletedEvent event = ChatCompletedEvent.builder()
                .memberId(1L)
                .userMessage("일정 보여줘")
                .assistantResponse("답변")
                .createdAt(LocalDateTime.of(2025, 1, 1, 9, 0))
                .build();

        // when
        consumer.handle(event, ack);

        // then
        verify(cachePort).increment(eq("pattern:time:1"), eq("morning"), eq(1L));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("오후 시간대 채팅 - time 패턴에 afternoon 누적")
    void handle_afternoonHour_incrementsAfternoonPattern() {
        // given
        when(scheduleRepositoryPort.countByCategoryForMember(1L)).thenReturn(List.of());

        ChatCompletedEvent event = ChatCompletedEvent.builder()
                .memberId(1L)
                .userMessage("일정 보여줘")
                .assistantResponse("답변")
                .createdAt(LocalDateTime.of(2025, 1, 1, 15, 0))
                .build();

        // when
        consumer.handle(event, ack);

        // then
        verify(cachePort).increment(eq("pattern:time:1"), eq("afternoon"), eq(1L));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("사용자가 쓰는 카테고리 이름이 메시지에 포함 - interest 패턴에 해당 카테고리 누적")
    void handle_messageContainsUserCategory_incrementsCategoryPattern() {
        // given
        when(scheduleRepositoryPort.countByCategoryForMember(1L))
                .thenReturn(List.of(new CategoryFrequency(10L, 5L)));
        when(categoryRepositoryPort.findById(10L))
                .thenReturn(CategoryModel.builder().id(10L).name("운동").build());

        ChatCompletedEvent event = ChatCompletedEvent.builder()
                .memberId(1L)
                .userMessage("운동 일정 잡아줘")
                .assistantResponse("답변")
                .createdAt(LocalDateTime.of(2025, 1, 1, 9, 0))
                .build();

        // when
        consumer.handle(event, ack);

        // then
        verify(cachePort).increment(eq("pattern:interest:1"), eq("운동"), eq(1L));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("메시지에 사용자 카테고리 이름이 없을 때 - interest 패턴은 저장 안 됨")
    void handle_messageWithoutUserCategory_noInterestPatternSaved() {
        // given
        when(scheduleRepositoryPort.countByCategoryForMember(1L))
                .thenReturn(List.of(new CategoryFrequency(10L, 5L)));
        when(categoryRepositoryPort.findById(10L))
                .thenReturn(CategoryModel.builder().id(10L).name("운동").build());

        ChatCompletedEvent event = ChatCompletedEvent.builder()
                .memberId(1L)
                .userMessage("일정 보여줘")
                .assistantResponse("답변")
                .createdAt(LocalDateTime.of(2025, 1, 1, 9, 0))
                .build();

        // when
        consumer.handle(event, ack);

        // then: 시간대 패턴은 메시지 내용과 무관하게 항상 누적되지만,
        // interest 패턴은 메시지에 카테고리 이름이 없으면 호출되지 않는다.
        verify(cachePort, never()).increment(eq("pattern:interest:1"), any(), anyLong());
        verify(ack).acknowledge();
    }
}
