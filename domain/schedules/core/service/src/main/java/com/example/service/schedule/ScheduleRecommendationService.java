package com.example.service.schedule;

import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.interfaces.auth.AuthInterface;
import com.example.model.schedules.SchedulesModel;
import com.example.outbound.openai.config.OpenAiWebClient;
import com.example.outbound.openai.dto.OpenAiRequest;
import com.example.outbound.openai.dto.ScheduleRecommendationDto;
import com.example.outbound.openai.dto.TimeSlot;
import com.example.rdbrepository.Schedules;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class ScheduleRecommendationService {

    private final ScheduleRepositoryPort scheduleRepositoryPort;

    private final AuthInterface authInterface;

    private final ScheduleRecommendCacheService recommendCacheService;

    private final OpenAiWebClient aiClient;

    private final ObjectMapper objectMapper;

    @CircuitBreaker(name = "openAiClient", fallbackMethod = "fallbackRecommendSchedules")
    public Mono<List<SchedulesModel>> recommendSchedules(String accessToken, Pageable pageable) {
        //회원 번호 추출
        Long memberId = authInterface.currentUserId(accessToken);
        log.info("memberId::"+memberId);

        String cacheKey = "schedule:recommend:" + memberId + ":" + LocalDate.now();
        List<SchedulesModel> schedulesSize = scheduleRepositoryPort.findAllByMemberId(memberId, pageable).getContent();
        log.info("가져온 일정 수: {}", schedulesSize.size());
        // 일정 캐시 확인.
        Optional<List<SchedulesModel>> cached = recommendCacheService.get(cacheKey, new TypeReference<>() {
        });
       log.info("cached?:"+cached.toString());
        if (cached.isPresent()) {
            log.info("캐시 히트: {}", cacheKey);
            return Mono.just(cached.get());
        }

        return Mono.just(scheduleRepositoryPort.findAllByMemberId(memberId, pageable).getContent())
                .map(this::mapToSchedules)
                .doOnNext(schedules -> {
                    List<TimeSlot> conflictSlots = schedules.stream()
                            .map(s -> new TimeSlot(s.getStartTime(), s.getEndTime()))
                            .collect(Collectors.toList());
                    log.info(conflictSlots.toString());
                    String conflictKey = "schedule:conflict:" + memberId + ":" + LocalDate.now();
                    log.info(conflictKey);
                    recommendCacheService.set(conflictKey, conflictSlots, Duration.ofDays(1));
                })
                .map(this::buildPromptFromSchedules)
                .map(this::buildOpenAiRequest)
                .flatMap(request -> aiClient.getChatCompletion(request))
                .doOnError(e -> log.error("[OpenAI 호출 실패] memberId={}, 이유: {}", memberId, e.getMessage(), e))
                .map(response -> {
                    String content = response.getChoices().get(0).getMessage().getContent();
                    log.info("AI 응답 원본:\n{}", content);

                    String extractedJson = extractJsonFromContent(content);
                    log.info("추출된 JSON:\n{}", extractedJson);

                    List<ScheduleRecommendationDto> dtoList = parseJson(extractedJson);
                    List<SchedulesModel> recommended = mapToRecommendedSchedules(dtoList, memberId);
                    recommendCacheService.set(cacheKey, recommended, Duration.ofHours(6));
                    return recommended;
                });
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

    private List<Schedules> mapToSchedules(List<SchedulesModel> scheduleModels) {
        List<Schedules>result = scheduleModels.stream().map(m ->
                Schedules.builder()
                        .id(m.getId())
                        .contents(Optional.ofNullable(m.getContents()).orElse(""))
                        .scheduleMonth(m.getScheduleMonth())
                        .scheduleDay(m.getScheduleDays())
                        .memberId(m.getMemberId())
                        .categoryId(m.getCategoryId())
                        .startTime(m.getStartTime())
                        .endTime(m.getEndTime())
                        .build()
        ).collect(Collectors.toList());
        log.info(result.toString());
        return result;
    }

    private List<SchedulesModel> mapToRecommendedSchedules(List<ScheduleRecommendationDto> dtoList, Long memberId) {
        List<SchedulesModel> recommendedSchedules = new ArrayList<>();

        for (ScheduleRecommendationDto dto : dtoList) {
            String contents;
            Object rawContents = dto.getContents();
            log.info(rawContents.toString());
            // contents 타입 방어 (최신 Jackson이면 타입 강제 가능, 구버전이면 아래처럼 처리)
            if (rawContents instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> listContents = (List<String>) rawContents;
                contents = String.join(",", listContents);
                log.info("result::"+contents);
            } else if (rawContents != null) {
                contents = rawContents.toString();
            } else {
                contents = "";
            }
            Integer scheduleMonth = (dto.getStartTime() != null) ? dto.getStartTime().getMonthValue() : null;
            Integer scheduleDay = (dto.getStartTime() != null) ? dto.getStartTime().getDayOfMonth() : null;

            log.info("contents::"+contents);
            recommendedSchedules.add(SchedulesModel.builder()
                    .contents(contents)
                    .scheduleMonth(scheduleMonth)
                    .scheduleDays(scheduleDay)
                    .startTime(dto.getStartTime())
                    .endTime(dto.getEndTime())
                    .progressStatus(PROGRESS_STATUS.IN_COMPLETE)
                    .memberId(memberId)
                    .build());
        }

        return recommendedSchedules;
    }

    private String buildPromptFromSchedules(List<Schedules> schedules) {

        StringBuilder prompt = new StringBuilder();
        LocalDate baseDate = LocalDate.now();

        String jsonFormatInstruction = "\n응답은 항상 JSON 배열 형식으로만 제공해야 합니다. 다른 설명 텍스트는 일체 포함하지 마세요.\n";
        jsonFormatInstruction += "각 객체는 **반드시** \"contents\", \"startTime\", \"endTime\" 필드를 포함해야 합니다.\n";
        jsonFormatInstruction += "\"startTime\"과 \"endTime\"은 **절대 null이 될 수 없으며**, \"yyyy-MM-dd'T'HH:mm\" 형식의 문자열이어야 합니다.\n";
        String jsonExample = String.format("```json\n[\n  {\"contents\": \"개발 공부\", \"startTime\": \"%sT14:00\", \"endTime\": \"%sT15:00\"}\n]\n```", baseDate, baseDate);

        if (schedules.isEmpty()) {
            prompt.append("사용자의 일정이 없습니다. 오늘 하루를 알차게 보낼 수 있는 생산적인 활동 3가지와 휴식 2가지를 포함하여 총 5개의 일정을 추천해 주세요.\n");
            prompt.append("추천 일정은 하루 전체 시간대를 고려하여 균형 있게 구성해주세요. 예를 들어 아침, 점심, 저녁 시간대에 각각 배치하는 것이 좋습니다.\n");
            prompt.append("각 활동의 소요 시간은 1시간을 넘지 않도록 해주세요.\n");
            prompt.append(jsonFormatInstruction);
            prompt.append(jsonExample);
        } else {
            // 사용자의 첫 일정 기준으로 날짜를 추출 (모든 일정이 동일 날짜라는 전제)
            Schedules first = schedules.get(0);
            LocalDateTime firstStartTime = first.getStartTime(); // LocalDateTime을 가져옴
            log.info(firstStartTime.toString());
            // startTime의 날짜 정보를 baseDate로 사용
            baseDate = firstStartTime.toLocalDate();
            // 사용자 일정 빈도 찾기.
            Map<String, Long> contentsFrequency = schedules.stream()
                    .collect(Collectors.groupingBy(
                            s -> s.getContents() != null ? s.getContents() : "",
                            Collectors.counting()));

            String topContent = contentsFrequency.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            log.info(topContent);

            if (topContent != null && !topContent.isBlank()) {
                prompt.append("사용자는 '").append(topContent)
                        .append("' 활동을 자주 합니다. 이와 관련된 추천 일정을 포함해주세요.\n");
            }

            prompt.append("아래는 사용자의 ").append(baseDate).append(" 일정 목록입니다.\n");

            for (Schedules schedule : schedules) {

                String contents = schedule.getContents() != null ? schedule.getContents() : "";
                LocalDate date = schedule.getStartTime().toLocalDate();
                log.info("ID: {}, Contents: '{}', Start: {}, End: {}", schedule.getId(), schedule.getContents(), schedule.getStartTime(), schedule.getEndTime());
                prompt.append("- ")
                        .append(date).append(" ")
                        .append(schedule.getStartTime().toLocalTime()).append("~")
                        .append(schedule.getEndTime().toLocalTime()).append(" : ")
                        .append(contents).append("\n");
            }

            // 시간대 분석
            boolean hasMorning = schedules.stream().anyMatch(s -> s.getStartTime().getHour() < 10);
            boolean hasEvening = schedules.stream().anyMatch(s -> s.getStartTime().getHour() >= 18);

            // 빈시간 찾기.
            List<TimeSlot> timeSlots = findAvailableTimeSlots(schedules, Duration.ofMinutes(30));

            if (!timeSlots.isEmpty()) {
                prompt.append("\n사용자의 일정 사이 빈 시간은 다음과 같습니다:\n");
                for (TimeSlot slot : timeSlots) {
                    prompt.append("- ")
                            .append(slot.start().toLocalTime()).append(" ~ ")
                            .append(slot.end().toLocalTime()).append("\n");
                }
                prompt.append("이 시간대에 적절한 일정을 추천해주세요.\n");
            }

            if (!hasMorning) prompt.append("사용자는 아침 일정이 없습니다. 아침 추천을 포함해주세요.\n");
            if (!hasEvening) prompt.append("사용자는 저녁 일정이 없습니다. 저녁 추천을 포함해주세요.\n");

            // 일정이 많은 경우.
            if (schedules.size() >= 10) {
                prompt.append("사용자의 일정이 많습니다. 과부하 상태일 수 있으므로 여유 일정을 추천해주세요.\n");
            }

            long totalMinutes = schedules.stream()
                    .mapToLong(s -> Duration.between(s.getStartTime(), s.getEndTime()).toMinutes())
                    .sum();

            if (totalMinutes >= 360) {
                prompt.append("총 일정 시간이 6시간 이상입니다. 피로할 수 있으니 휴식 위주 일정도 고려해주세요.\n");
            }
        }
        // 예시에서도 baseDate 사용
        prompt.append("\n응답은 항상 JSON 배열 형식으로만 제공해야 합니다. 설명이나 다른 텍스트는 절대 포함하지 마세요. 각 객체는 반드시 \"contents\", \"startTime\", \"endTime\" 필드를 포함해야 합니다. \"startTime\"과 \"endTime\"은 \"yyyy-MM-dd'T'HH:mm\" 형식의 문자열이어야 합니다.\n")
                .append("```json\n")
                .append("[\n")
                .append("  {\"contents\": \"운동\", \"startTime\": \"").append(baseDate).append("T14:00\", \"endTime\": \"").append(baseDate).append("T15:00\"}\n")
                .append("]\n")
                .append("```");

        return prompt.toString();
    }

    private OpenAiRequest buildOpenAiRequest(String prompt) {
        return OpenAiRequest.builder()
                .model("gpt-4o")
                .messages(List.of(OpenAiRequest
                        .Message
                        .builder()
                        .role("user")
                        .content(prompt)
                        .build()))
                .build();
    }

    private String extractJsonFromContent(String content) {
        int startIndex = content.indexOf('[');
        int endIndex = content.lastIndexOf(']');

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return content.substring(startIndex, endIndex + 1);
        }

        // JSON 형식이 아닌 경우, 기본값 또는 예외 처리
        log.warn("AI 응답에서 JSON 배열을 추출하지 못했습니다: {}", content);
        return "[]"; // 빈 JSON 배열 반환
    }

    private List<ScheduleRecommendationDto> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("AI 응답 파싱 중 오류", e);
        }
    }

    private List<TimeSlot> findAvailableTimeSlots(List<Schedules> schedules, Duration minimumGap) {
        schedules.sort(Comparator.comparing(Schedules::getStartTime));
        List<TimeSlot> availableSlots = new ArrayList<>();

        for (int i = 0; i < schedules.size() - 1; i++) {
            LocalDateTime endCurrent = schedules.get(i).getEndTime();
            LocalDateTime startNext = schedules.get(i + 1).getStartTime();
            Duration gap = Duration.between(endCurrent, startNext);

            if (gap.compareTo(minimumGap) >= 0) {
                availableSlots.add(new TimeSlot(endCurrent, startNext));
            }
        }
        return availableSlots;
    }
}
