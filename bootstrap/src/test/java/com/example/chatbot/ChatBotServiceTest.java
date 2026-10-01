package com.example.chatbot;

import com.example.events.spring.ChatCompletedEvent;
import com.example.inbound.schedules.ScheduleRecommendationCachePort;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.interfaces.category.CategoryRepositoryPort;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ChatBotServiceTest {

    @Mock
    private ScheduleRecommendationCachePort cachePort;

    @Mock
    private ScheduleRepositoryPort scheduleRepositoryPort;

    @Mock
    private CategoryRepositoryPort categoryRepositoryPort;

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
    @DisplayName("답변 스트리밍이 5초를 넘겨도 끝나면 대화 이력을 저장한다")
    void streamChat_longStream_stillPublishesHistory() {
        // given
        given(cachePort.getChatHistory(memberId)).willReturn(List.of());
        given(scheduleRepositoryPort.findAllByMemberId(any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(openAiRequestBuilder.buildWithMessages(any()))
                .willReturn(OpenAiRequest.builder().model("gpt-4o").messages(List.of()).build());
        // 토큰 3개가 2.1초 간격으로 와서 전체 약 6.3초 (예전에는 체인 끝 5초 타임아웃에 걸려 저장이 생략됐다)
        // 저장 타임아웃(5초)과 subscribeOn 스케줄링의 실제 순서를 보려고 가상 시간이 아닌 실제 시간으로 돌린다.
        given(openAiWebClient.streamChatCompletion(any()))
                .willReturn(Flux.just("긴 ", "답변 ", "입니다").delayElements(Duration.ofMillis(2100)));

        // when & then
        StepVerifier.create(chatBotService.streamChat(memberId, userMessage))
                .expectNext("긴 ", "답변 ", "입니다")
                .expectComplete()
                .verify(Duration.ofSeconds(15));

        ArgumentCaptor<ChatCompletedEvent> eventCaptor = ArgumentCaptor.forClass(ChatCompletedEvent.class);
        verify(chatEventPort, times(1)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAssistantResponse()).isEqualTo("긴 답변 입니다");
    }

    @Test
    @DisplayName("OpenAI 호출 실패 시 대체 응답을 보내고 대화 이력은 저장하지 않음")
    void streamChat_openAiError_fallbackWithoutHistory() {
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
                .expectNextSequence(ChatBotService.FALLBACK_TOKENS)
                .verifyComplete();

        verify(chatEventPort, never()).publish(any());
    }

    @Test
    @DisplayName("답변 도중 스트림이 끊기면 받은 토큰 뒤에 대체 응답을 붙이고, 불완전한 답변은 저장하지 않음")
    void streamChat_midStreamError_noPartialHistory() {
        // given
        given(cachePort.getChatHistory(memberId)).willReturn(List.of());
        given(scheduleRepositoryPort.findAllByMemberId(any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(openAiRequestBuilder.buildWithMessages(any()))
                .willReturn(OpenAiRequest.builder().model("gpt-4o").messages(List.of()).build());
        given(openAiWebClient.streamChatCompletion(any()))
                .willReturn(Flux.concat(Flux.just("이번 주 ", "일정은"),
                        Flux.error(new RuntimeException("연결 끊김"))));

        // when & then
        StepVerifier.create(chatBotService.streamChat(memberId, userMessage))
                .expectNext("이번 주 ", "일정은")
                .expectNextSequence(ChatBotService.FALLBACK_TOKENS)
                .verifyComplete();

        verify(chatEventPort, never()).publish(any());
    }
}
