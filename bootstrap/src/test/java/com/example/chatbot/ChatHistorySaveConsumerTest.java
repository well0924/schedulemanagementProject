package com.example.chatbot;

import com.example.events.spring.ChatCompletedEvent;
import com.example.inbound.consumer.chatbot.ChatHistorySaveConsumer;
import com.example.inbound.schedules.ChatHistoryPort;
import com.example.inbound.schedules.ScheduleRecommendationCachePort;
import com.example.model.schedules.ChatHistoryModel;
import com.example.outbound.openai.dto.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatHistorySaveConsumerTest {

    @Mock
    private ChatHistoryPort chatHistoryPort;

    @Mock
    private ScheduleRecommendationCachePort cachePort;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private ChatHistorySaveConsumer consumer;

    @Test
    @DisplayName("정상 소비 - DB 저장 + Redis 갱신 + ack 커밋")
    void handle_success() {
        // given
        ChatCompletedEvent event = ChatCompletedEvent.builder()
                .memberId(1L)
                .userMessage("질문")
                .assistantResponse("답변")
                .createdAt(LocalDateTime.now())
                .build();

        // when
        consumer.handle(event, ack);

        // then - DB 저장 확인
        ArgumentCaptor<ChatHistoryModel> modelCaptor =
                ArgumentCaptor.forClass(ChatHistoryModel.class);
        verify(chatHistoryPort).save(modelCaptor.capture());
        assertThat(modelCaptor.getValue().getMemberId()).isEqualTo(1L);
        assertThat(modelCaptor.getValue().getUserMessage()).isEqualTo("질문");

        // Redis 갱신 2번 확인 (user + assistant)
        verify(cachePort, times(2)).appendChatMessage(ArgumentMatchers.eq(1L), any(ChatMessage.class));

        // ack 커밋 확인
        verify(ack, times(1)).acknowledge();
    }

    @Test
    @DisplayName("DB 저장 실패 시 예외 전파 - DLQ로 이동")
    void handle_dbFail_throwException() {
        // given
        ChatCompletedEvent event = ChatCompletedEvent.builder()
                .memberId(1L)
                .userMessage("질문")
                .assistantResponse("답변")
                .createdAt(LocalDateTime.now())
                .build();

        doThrow(new RuntimeException("DB 저장 실패"))
                .when(chatHistoryPort).save(any(ChatHistoryModel.class));


        // when & then
        assertThatThrownBy(() -> consumer.handle(event, ack))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 저장 실패");

        // ack 커밋 안 됨 확인
        verify(ack, times(0)).acknowledge();
    }
}
