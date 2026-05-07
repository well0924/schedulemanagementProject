package com.example.service.schedule.recommend;

import com.example.events.spring.ChatCompletedEvent;
import com.example.inbound.schedules.ScheduleRecommendationCachePort;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.interfaces.notification.chatbot.ChatEventPort;
import com.example.model.schedules.SchedulesModel;
import com.example.outbound.openai.config.OpenAiWebClient;
import com.example.outbound.openai.dto.ChatMessage;
import com.example.outbound.openai.dto.OpenAiRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatBotService {

    private final ScheduleRecommendationCachePort cacheService;  // 이력 관리
    private final ScheduleRepositoryPort scheduleRepositoryPort;
    private final OpenAiWebClient openAiWebClient;
    private final OpenAiRequestBuilder openAiRequestBuilder;
    private final ChatEventPort chatEventPort;

    public ChatBotService(
            ScheduleRecommendationCachePort cacheService,
            ScheduleRepositoryPort scheduleRepositoryPort,
            OpenAiWebClient openAiWebClient,
            OpenAiRequestBuilder openAiRequestBuilder,
            @Qualifier("chatOutboxAdapter") ChatEventPort chatEventPort
    ) {
        this.cacheService = cacheService;
        this.scheduleRepositoryPort = scheduleRepositoryPort;
        this.openAiWebClient = openAiWebClient;
        this.openAiRequestBuilder = openAiRequestBuilder;
        this.chatEventPort = chatEventPort;
    }

    /**
     * 사용자의 질문에 대해 일정 데이터를 참고하여 AI 응답을 스트리밍한다.
     * @param memberId 사용자 ID
     * @param userMessage 사용자의 질문
     * @return AI 응답 조각들의 Flux (Streaming)
     */
    public Flux<String> streamChat(Long memberId, String userMessage) {

        // 1. 이전 대화 이력 조회 (비동기)
        Mono<List<ChatMessage>> historyMono = Mono
                .fromCallable(() -> cacheService
                        .getChatHistory(memberId))
                .subscribeOn(Schedulers.boundedElastic());

        // 2. 사용자 일정 조회 (비동기)
        Mono<List<SchedulesModel>> schedulesMono = Mono
                .fromCallable(() ->
                        scheduleRepositoryPort
                                .findAllByMemberId(memberId, Pageable.ofSize(5)).getContent())
                .subscribeOn(Schedulers.boundedElastic());

        // 3. 두 데이터(이력 + 일정)가 모두 준비될 때까지 기다렸다가 조합 (병렬 처리로 성능 최적화)
        return Mono.zip(historyMono, schedulesMono)
                .flatMapMany(tuple -> {
                    // OpenAI 요청 객체 생성 (프롬프트 구성)
                    OpenAiRequest request = buildChatRequest(tuple.getT1(), tuple.getT2(), userMessage);
                    // OpenAI 스트리밍 호출 시작
                    return openAiWebClient
                            .streamChatCompletion(request)
                            .publish(sharedFlux -> {
                                // (A) 클라이언트에게 실시간으로 토큰 전달
                                Flux<String>clientStream = sharedFlux;
                                // (B) 전체 응답을 수지하여 Outbox에 저장(체인에 통합)
                                Mono<Void> saveOutboxMono = sharedFlux
                                        .collect(Collectors.joining())
                                        .flatMap(fullResponse -> Mono
                                                .fromRunnable(()->{
                                                    ChatCompletedEvent event = ChatCompletedEvent.builder()
                                                            .memberId(memberId)
                                                            .userMessage(userMessage)
                                                            .assistantResponse(fullResponse)
                                                            .createdAt(LocalDateTime.now())
                                                            .build();
                                                    log.info("발행될 이벤트 ID: {}", event.getEventId());
                                                    chatEventPort.publish(event);
                                                })
                                                .subscribeOn(Schedulers.boundedElastic()))
                                        .timeout(Duration.ofSeconds(5)) // 저장 프로세스에 타임아웃 부여
                                        .onErrorResume(e -> {
                                            // 저장 실패 시 로그만 남기고 사용자 응답은 유지 [cite: 80, 98]
                                            log.error("[Outbox 저장 실패] memberId={}, reason={}", memberId, e.getMessage());
                                            return Mono.empty();
                                        })
                                        .then();
                                return clientStream.mergeWith(saveOutboxMono
                                        .thenMany(Flux.empty()));
                            });

                });
    }

    /**
     * OpenAI에 보낼 메시지 리스트를 구성한다. (System, Assistant, User 역할 부여)
     */
    private OpenAiRequest buildChatRequest(
            List<ChatMessage> history,
            List<SchedulesModel> schedules,
            String userMessage) {

        List<OpenAiRequest.Message> messages = new ArrayList<>();

        // 시스템 프롬프트
        messages.add(new OpenAiRequest.Message("system",
                "당신은 일정 관리 도우미입니다. 사용자의 일정 데이터를 기반으로 답변하세요.\n" +
                        "현재 일정: " + schedules));

        // 이전 대화 이력
        history.forEach(h -> messages.add(
                new OpenAiRequest.Message(h.role(), h.content())));

        // 현재 질문
        messages.add(new OpenAiRequest.Message("user", userMessage));

        return openAiRequestBuilder.buildWithMessages(messages);
    }
}
