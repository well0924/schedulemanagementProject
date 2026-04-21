package com.example.service.schedule.recommend;

import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.interfaces.auth.AuthInterface;
import com.example.model.schedules.SchedulesModel;
import com.example.outbound.openai.config.OpenAiWebClient;
import com.example.outbound.openai.dto.OpenAiRequest;
import com.example.outbound.openai.dto.OpenAiResponse;
import com.example.outbound.openai.dto.ScheduleRecommendationDto;
import com.example.rdbrepository.Schedules;
import com.example.schedules.mapper.ScheduleRecommendMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class ScheduleRecommendationService {

    private final ScheduleRepositoryPort scheduleRepositoryPort;

    private final AuthInterface authInterface;

    private final ScheduleRecommendCacheService recommendCacheService;

    private final OpenAiWebClient aiClient;

    private final SchedulePromptBuilder promptBuilder;

    private final ScheduleRecommendMapper recommendMapper;

    private final OpenAiRequestBuilder openAiRequestBuilder;

    @CircuitBreaker(name = "openAiClient", fallbackMethod = "fallbackRecommendSchedules")
    public Mono<List<SchedulesModel>> recommendSchedules(String accessToken, Pageable pageable) {
        //회원 번호 추출
        Long memberId = authInterface.currentUserId(accessToken);
        log.info("memberId::"+memberId);

        String cacheKey = "schedule:recommend:" + memberId + ":" + LocalDate.now();
        // 일정 캐시 확인.
        Optional<List<SchedulesModel>> cached = recommendCacheService.get(cacheKey, new TypeReference<>() {
        });

        log.info("cached?:"+cached.toString());

        if (cached.isPresent()) {
            log.info("캐시 히트: {}", cacheKey);
            return Mono.just(cached.get());
        }

        return  Mono.just(scheduleRepositoryPort
                        .findAllByMemberId(memberId, pageable).getContent())
                .map(this::buildRequest)
                .flatMap(aiClient::getChatCompletion)
                .doOnError(e -> log.error("[OpenAI 호출 실패] memberId={}, 이유: {}",
                        memberId, e.getMessage(), e))
                .map(response -> handleResponse(response, memberId, cacheKey));
    }

    // fallback 메서드
    public Mono<List<SchedulesModel>> fallbackRecommendSchedules(String userId, Pageable pageable, Throwable t) {
        log.error("[OpenAI fallback 작동] userId={}, 이유: {}", userId, t.getMessage(), t);

        Long memberId = authInterface.currentUserId(userId);

        SchedulesModel fallbackSchedule = SchedulesModel
                .builder()
                .contents("AI 추천 일정 제공 불가 - 기본 일정")
                .scheduleMonth(LocalDateTime.now().getMonthValue())
                .scheduleDays(LocalDateTime.now().getDayOfMonth())
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusHours(1))
                .memberId(memberId)
                .build();

        return Mono.just(List.of(fallbackSchedule));
    }


    // 요청 생성
    private OpenAiRequest buildRequest(List<SchedulesModel> models) {
        List<Schedules> entities = recommendMapper.toEntityList(models);
        String prompt = promptBuilder.build(entities);
        return openAiRequestBuilder.build(prompt);
    }

    // 응답 처리
    private List<SchedulesModel> handleResponse(
            OpenAiResponse response, Long memberId, String cacheKey) {
        String content = response.getChoices().get(0).getMessage().getContent();
        String json = recommendMapper.extractJson(content);
        List<ScheduleRecommendationDto> dtoList = recommendMapper.parseJson(json);
        List<SchedulesModel> recommended = recommendMapper.toModelList(dtoList, memberId);
        recommendCacheService.set(cacheKey, recommended, Duration.ofHours(6));
        return recommended;
    }
}
