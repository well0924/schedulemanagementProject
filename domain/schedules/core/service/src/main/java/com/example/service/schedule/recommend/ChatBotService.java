package com.example.service.schedule.recommend;

import com.example.events.spring.ChatCompletedEvent;
import com.example.inbound.schedules.ScheduleRecommendationCachePort;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.interfaces.category.CategoryRepositoryPort;
import com.example.interfaces.notification.chatbot.ChatEventPort;
import com.example.model.category.CategoryModel;
import com.example.model.schedules.CategoryFrequency;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatBotService {

    private final ScheduleRecommendationCachePort cacheService;  // 이력/패턴 관리
    private final ScheduleRepositoryPort scheduleRepositoryPort;
    private final CategoryRepositoryPort categoryRepositoryPort;
    private final OpenAiWebClient openAiWebClient;
    private final OpenAiRequestBuilder openAiRequestBuilder;
    private final ChatEventPort chatEventPort;

    public ChatBotService(
            ScheduleRecommendationCachePort cacheService,
            ScheduleRepositoryPort scheduleRepositoryPort,
            CategoryRepositoryPort categoryRepositoryPort,
            OpenAiWebClient openAiWebClient,
            OpenAiRequestBuilder openAiRequestBuilder,
            @Qualifier("chatOutboxAdapter") ChatEventPort chatEventPort
    ) {
        this.cacheService = cacheService;
        this.scheduleRepositoryPort = scheduleRepositoryPort;
        this.categoryRepositoryPort = categoryRepositoryPort;
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

        // 컨텍스트 조회 단계는 하나가 실패해도 전체 응답이 죽지 않도록,
        // 각각 타임아웃 + 빈 값 fallback을 둔다.
        Mono<List<ChatMessage>> historyMono = Mono
                .fromCallable(() -> cacheService.getChatHistory(memberId))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(2))
                .onErrorResume(e -> {
                    log.error("[streamChat] 대화 이력 조회 실패, 빈 이력으로 진행 - memberId={}, reason={}", memberId, e.getMessage());
                    return Mono.just(List.of());
                });

        Mono<List<SchedulesModel>> schedulesMono = Mono
                .fromCallable(() -> scheduleRepositoryPort
                        .findAllByMemberId(memberId, Pageable.ofSize(5)).getContent())
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(2))
                .onErrorResume(e -> {
                    log.error("[streamChat] 최근 일정 조회 실패, 빈 목록으로 진행 - memberId={}, reason={}", memberId, e.getMessage());
                    return Mono.just(List.of());
                });

        Mono<List<CategoryFrequency>> categoryFrequencyMono = Mono
                .fromCallable(() -> scheduleRepositoryPort.countByCategoryForMember(memberId))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(2))
                .onErrorResume(e -> {
                    log.error("[streamChat] 카테고리 빈도 조회 실패, 빈 목록으로 진행 - memberId={}, reason={}", memberId, e.getMessage());
                    return Mono.just(List.of());
                });

        Mono<Map<String, Long>> timePatternMono = Mono
                .fromCallable(() -> cacheService.getHash("pattern:time:" + memberId))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(1))
                .onErrorResume(e -> {
                    log.error("[streamChat] 시간대 패턴 조회 실패, 무시하고 진행 - memberId={}, reason={}", memberId, e.getMessage());
                    return Mono.just(Map.of());
                });

        Mono<Map<String, Long>> interestPatternMono = Mono
                .fromCallable(() -> cacheService.getHash("pattern:interest:" + memberId))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(1))
                .onErrorResume(e -> {
                    log.error("[streamChat] 관심사 패턴 조회 실패, 무시하고 진행 - memberId={}, reason={}", memberId, e.getMessage());
                    return Mono.just(Map.of());
                });

        Mono<RecommendationContext> contextMono = Mono
                .zip(historyMono, schedulesMono, categoryFrequencyMono, timePatternMono, interestPatternMono)
                .flatMap(tuple -> {
                    List<SchedulesModel> schedules = tuple.getT2();
                    List<CategoryFrequency> frequencies = tuple.getT3();

                    Set<Long> categoryIds = new LinkedHashSet<>();
                    schedules.forEach(s -> {
                        if (s.getCategoryId() != null) categoryIds.add(s.getCategoryId());
                    });
                    frequencies.forEach(f -> categoryIds.add(f.categoryId()));

                    return Mono.fromCallable(() -> resolveCategoryNames(categoryIds))
                            .subscribeOn(Schedulers.boundedElastic())
                            .timeout(Duration.ofSeconds(2))
                            .onErrorResume(e -> {
                                log.error("[streamChat] 카테고리명 조회 실패, 빈 매핑으로 진행 - reason={}", e.getMessage());
                                return Mono.just(Map.of());
                            })
                            .map(categoryNames -> new RecommendationContext(
                                    tuple.getT1(), schedules, frequencies, categoryNames,
                                    tuple.getT4(), tuple.getT5()));
                });

        // 두 데이터(이력 + 일정 + 카테고리 + 패턴)가 모두 준비될 때까지 기다렸다가 조합
        return contextMono.flatMapMany(ctx -> {
            // OpenAI 요청 객체 생성 (프롬프트 구성)
            OpenAiRequest request = buildChatRequest(ctx, userMessage);
            // OpenAI 스트리밍 호출 시작
            return openAiWebClient
                    .streamChatCompletion(request)
                    .publish(sharedFlux -> {
                        // (A) 클라이언트에게 실시간으로 토큰 전달
                        Flux<String> clientStream = sharedFlux;
                        // (B) 전체 응답을 수집하여 Outbox에 저장(체인에 통합)
                        Mono<Void> saveOutboxMono = sharedFlux
                                .collect(Collectors.joining())
                                .flatMap(fullResponse -> Mono
                                        .fromRunnable(() -> {
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
                                    // 스트림이 실패하면 collect도 실패해 여기로 온다 → 불완전한 답변은 저장하지 않는다.
                                    // 저장 자체가 실패한 경우에도 로그만 남기고 사용자 응답은 유지
                                    log.warn("[대화 이력 저장 생략] memberId={}, reason={}", memberId, e.toString());
                                    return Mono.empty();
                                })
                                .then();
                        return clientStream.mergeWith(saveOutboxMono
                                .thenMany(Flux.empty()));
                    });

        }).onErrorResume(e -> {
            // OpenAI 실패·서킷 OPEN 시 사용자에게는 안내 문구를 보내되, 이력으로는 남기지 않는다
            log.warn("[챗봇 대체 응답] memberId={}, reason={}", memberId, e.toString());
            return Flux.fromIterable(FALLBACK_TOKENS);
        });
    }

    public static final List<String> FALLBACK_TOKENS = List.of(
            "죄송합니다. ", "현재 ", "AI ", "연결이 ", "원활하지 ", "않습니다. ",
            "잠시 ", "후 ", "다시 ", "시도해 ", "주세요.");

    /**
     * 최근 일정 + 카테고리 빈도에 등장하는 categoryId들의 이름을 조회한다.
     * 개별 조회 실패(삭제된 카테고리 등)는 건너뛰고 계속 진행한다.
     */
    private Map<Long, String> resolveCategoryNames(Set<Long> categoryIds) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Long id : categoryIds) {
            try {
                CategoryModel category = categoryRepositoryPort.findById(id);
                names.put(id, category.getName());
            } catch (Exception e) {
                log.warn("[streamChat] 카테고리 조회 실패, 건너뜀 - categoryId={}, reason={}", id, e.getMessage());
            }
        }
        return names;
    }

    /**
     * OpenAI에 보낼 메시지 리스트를 구성한다. (System, Assistant, User 역할 부여)
     */
    private OpenAiRequest buildChatRequest(RecommendationContext ctx, String userMessage) {

        List<OpenAiRequest.Message> messages = new ArrayList<>();

        // 시스템 프롬프트
        messages.add(new OpenAiRequest.Message("system",
                "당신은 일정 관리 도우미입니다. 사용자의 일정 데이터를 기반으로 답변하세요.\n"
                        + buildScheduleSummary(ctx) + "\n"
                        + buildCategoryFrequencySummary(ctx) + "\n"
                        + buildPatternSummary(ctx)));

        // 이전 대화 이력
        ctx.history().forEach(h -> messages.add(
                new OpenAiRequest.Message(h.role(), h.content())));

        // 현재 질문
        messages.add(new OpenAiRequest.Message("user", userMessage));

        return openAiRequestBuilder.buildWithMessages(messages);
    }

    private String buildScheduleSummary(RecommendationContext ctx) {
        if (ctx.schedules().isEmpty()) {
            return "현재 일정: 없음";
        }
        String lines = ctx.schedules().stream()
                .map(s -> String.format("- [%s] %s (%s ~ %s)",
                        ctx.categoryNames().getOrDefault(s.getCategoryId(), "미분류"),
                        s.getContents(),
                        s.getStartTime(),
                        s.getEndTime()))
                .collect(Collectors.joining("\n"));
        return "현재 일정:\n" + lines;
    }

    private String buildCategoryFrequencySummary(RecommendationContext ctx) {
        if (ctx.categoryFrequencies().isEmpty()) {
            return "";
        }
        String top = ctx.categoryFrequencies().stream()
                .limit(3)
                .map(f -> ctx.categoryNames().getOrDefault(f.categoryId(), "미분류") + " " + f.count() + "건")
                .collect(Collectors.joining(", "));
        return "자주 사용하는 카테고리: " + top;
    }

    private String buildPatternSummary(RecommendationContext ctx) {
        StringBuilder sb = new StringBuilder();

        Map<String, Long> time = ctx.timePattern();
        if (!time.isEmpty()) {
            long morning = time.getOrDefault("morning", 0L);
            long afternoon = time.getOrDefault("afternoon", 0L);
            sb.append("사용자는 주로 ")
                    .append(morning >= afternoon ? "아침" : "오후")
                    .append("에 활동합니다. ");
        }

        Map<String, Long> interest = ctx.interestPattern();
        if (!interest.isEmpty()) {
            interest.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .ifPresent(e -> sb.append("주요 관심사: ").append(e.getKey()).append("."));
        }

        return sb.toString();
    }

    /**
     * 프롬프트 구성에 필요한 컨텍스트를 한데 모은 것.
     */
    private record RecommendationContext(
            List<ChatMessage> history,
            List<SchedulesModel> schedules,
            List<CategoryFrequency> categoryFrequencies,
            Map<Long, String> categoryNames,
            Map<String, Long> timePattern,
            Map<String, Long> interestPattern
    ) {
    }
}
