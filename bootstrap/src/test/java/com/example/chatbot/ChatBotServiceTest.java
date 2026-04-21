package com.example.chatbot;

import com.example.events.spring.ChatCompletedEvent;
import com.example.inbound.schedules.ScheduleRecommendationCachePort;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.interfaces.notification.chatbot.ChatEventPort;
import com.example.outbound.openai.config.OpenAiWebClient;
import com.example.outbound.openai.dto.ChatMessage;
import com.example.outbound.openai.dto.OpenAiRequest;
import com.example.service.schedule.recommend.ChatBotService;
import com.example.service.schedule.recommend.OpenAiRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ChatBotServiceTest {

    @Mock
    private ScheduleRecommendationCachePort cachePort;

    @Mock
    private ScheduleRepositoryPort scheduleRepositoryPort;

    @Mock
    private OpenAiWebClient openAiWebClient;

    @Mock
    private OpenAiRequestBuilder openAiRequestBuilder;

    @Mock
    private ChatEventPort chatEventPort;

    @InjectMocks
    private ChatBotService chatBotService;

    private Long memberId;
    private String userMessage;

    @BeforeEach
    void setUp() {
        memberId = 1L;
        userMessage = "이번 주 일정 알려줘";
    }

    @Test
    @DisplayName("대화 이력 없을 때 정상 스트리밍")
    void streamChat_noHistory_success() {
        // given
        given(cachePort.getChatHistory(memberId))
                .willReturn(List.of());

        given(scheduleRepositoryPort.findAllByMemberId(eq(memberId), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        given(openAiRequestBuilder.buildWithMessages(any()))
                .willReturn(OpenAiRequest.builder()
                        .model("gpt-4o")
                        .messages(List.of())
                        .build());

        given(openAiWebClient.streamChatCompletion(any()))
                .willReturn(Flux.just("안녕", "하세요"));

        // when
        Flux<String> result = chatBotService.streamChat(memberId, userMessage);

        // then
        StepVerifier.create(result)
                .expectNext("안녕")
                .expectNext("하세요")
                .verifyComplete();
    }

    @Test
    @DisplayName("대화 이력 있을 때 이력 포함해서 요청")
    void streamChat_withHistory_includeHistory() {
        // given
        List<ChatMessage> history = List.of(
                ChatMessage.builder()
                        .role("user")
                        .content("이전 질문")
                        .createdAt(LocalDateTime.now())
                        .build(),
                ChatMessage.builder()
                        .role("assistant")
                        .content("이전 답변")
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        given(cachePort.getChatHistory(memberId)).willReturn(history);
        given(scheduleRepositoryPort.findAllByMemberId(eq(memberId), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));
        given(openAiRequestBuilder.buildWithMessages(any()))
                .willReturn(OpenAiRequest.builder().model("gpt-4o").messages(List.of()).build());
        given(openAiWebClient.streamChatCompletion(any()))
                .willReturn(Flux.just("응답"));

        // when & then
        StepVerifier.create(chatBotService.streamChat(memberId, userMessage))
                .expectNext("응답")
                .verifyComplete();

        // 이력 2개 + 시스템 프롬프트 1개 + 현재 질문 1개 = 4개
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(openAiRequestBuilder).buildWithMessages(captor.capture());
        assertThat(captor.getValue()).hasSize(4);
    }

    @Test
    @DisplayName("스트리밍 완료 후 Kafka 이벤트 발행")
    void streamChat_onComplete_publishKafkaEvent() {
        // given
        given(cachePort.getChatHistory(memberId)).willReturn(List.of());
        given(scheduleRepositoryPort.findAllByMemberId(any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(openAiRequestBuilder.buildWithMessages(any()))
                .willReturn(OpenAiRequest.builder().model("gpt-4o").messages(List.of()).build());
        given(openAiWebClient.streamChatCompletion(any()))
                .willReturn(Flux.just("안녕", "하세요"));

        // when
        StepVerifier.create(chatBotService.streamChat(memberId, userMessage))
                .expectNext("안녕")
                .expectNext("하세요")
                .verifyComplete();

        // then - Kafka 발행 확인
        ArgumentCaptor<ChatCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(ChatCompletedEvent.class);
        verify(chatEventPort, times(1)).publish(eventCaptor.capture());

        ChatCompletedEvent event = eventCaptor.getValue();
        assertThat(event.getMemberId()).isEqualTo(memberId);
        assertThat(event.getUserMessage()).isEqualTo(userMessage);
        assertThat(event.getAssistantResponse()).isEqualTo("안녕하세요"); // 누적값
    }

    @Test
    @DisplayName("OpenAI 호출 실패 시 에러 전파")
    void streamChat_openAiError_propagateError() {
        // given
        given(cachePort.getChatHistory(memberId)).willReturn(List.of());
        given(scheduleRepositoryPort.findAllByMemberId(any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(openAiRequestBuilder.buildWithMessages(any()))
                .willReturn(OpenAiRequest.builder().model("gpt-4o").messages(List.of()).build());
        given(openAiWebClient.streamChatCompletion(any()))
                .willReturn(Flux.error(new RuntimeException("OpenAI 호출 실패")));

        // when & then
        StepVerifier.create(chatBotService.streamChat(memberId, userMessage))
                .expectErrorMessage("OpenAI 호출 실패")
                .verify();
    }
}
