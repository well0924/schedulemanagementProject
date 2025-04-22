package com.example.schedule.openapi.service;

import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.model.schedules.SchedulesModel;
import com.example.rdb.member.MemberRepository;
import com.example.rdbrepository.Schedules;
import com.example.schedule.openapi.client.OpenAiClient;
import com.example.schedule.openapi.dto.OpenAiRequest;
import com.example.schedule.openapi.dto.OpenAiResponse;
import com.example.schedule.openapi.dto.ScheduleRecommendationDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.example.rdbrepository.ScheduleRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@AllArgsConstructor
public class ScheduleRecommendService {

    private final ScheduleRepository schedulesRepository;

    private final MemberRepository memberRepository;

    private final OpenAiClient aiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @CircuitBreaker(name = "openAiClient", fallbackMethod = "fallbackRecommendSchedules")
    public List<Schedules> recommendSchedules(String userId, Pageable pageable) throws Exception {
        Long memberId = memberRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."))
                .getId();

        List<SchedulesModel> schedulesModels = schedulesRepository.findAllByUserId(userId, pageable).getContent();
        List<Schedules> schedules = mapToSchedules(schedulesModels);

        String prompt = buildPromptFromSchedules(schedules);
        OpenAiRequest request = buildOpenAiRequest(prompt);

        OpenAiResponse response = aiClient.getChatCompletion(request);
        String rawContent = response.getChoices().get(0).getMessage().getContent();
        String extractedJson = extractJsonFromContent(rawContent);

        List<ScheduleRecommendationDto> recommendedList = objectMapper.readValue(extractedJson, new TypeReference<>() {});
        return mapToRecommendedSchedules(recommendedList, memberId);
    }

    public List<Schedules> fallbackRecommendSchedules(String userId, Pageable pageable, Throwable t) {
        log.error("[OpenAI fallback 작동] userId={}, 이유: {}", userId, t.getMessage(), t);
        Schedules fallbackSchedule = Schedules.builder()
                .contents("AI 추천 일정 제공 불가 - 기본 일정")
                .scheduleMonth(LocalDateTime.now().getMonthValue())
                .scheduleDay(LocalDateTime.now().getDayOfMonth())
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusHours(1))
                .userId(Long.parseLong(userId))
                .build();

        return List.of(fallbackSchedule);
    }

    private List<Schedules> mapToSchedules(List<SchedulesModel> scheduleModels) {
        List<Schedules> schedules = new ArrayList<>();
        for (SchedulesModel model : scheduleModels) {
            schedules.add(Schedules.builder()
                    .id(model.getId())
                    .contents(model.getContents())
                    .scheduleMonth(model.getScheduleMonth())
                    .scheduleDay(model.getScheduleDays())
                    .isDeletedScheduled(model.isDeletedScheduled())
                    .userId(model.getUserId())
                    .categoryId(model.getCategoryId())
                    .progress_status(String.valueOf(model.getProgressStatus()))
                    .startTime(model.getStartTime())
                    .endTime(model.getEndTime())
                    .build());
        }
        return schedules;
    }

    private List<Schedules> mapToRecommendedSchedules(List<ScheduleRecommendationDto> dtoList, Long memberId) {
        List<Schedules> recommendedSchedules = new ArrayList<>();
        int year = LocalDateTime.now().getYear();

        for (ScheduleRecommendationDto dto : dtoList) {
            LocalDateTime start = mergeDateAndTime(year, dto.getScheduleMonth(), dto.getScheduleDay(), dto.getStartTime());
            LocalDateTime end = mergeDateAndTime(year, dto.getScheduleMonth(), dto.getScheduleDay(), dto.getEndTime());

            recommendedSchedules.add(Schedules.builder()
                    .contents(dto.getContents())
                    .scheduleMonth(dto.getScheduleMonth())
                    .scheduleDay(dto.getScheduleDay())
                    .startTime(start)
                    .endTime(end)
                    .progress_status(PROGRESS_STATUS.IN_COMPLETE.getValue())
                    .userId(memberId)
                    .build());
        }

        return recommendedSchedules;
    }

    private String buildPromptFromSchedules(List<Schedules> schedules) {
        StringBuilder prompt = new StringBuilder();

        if (schedules.isEmpty()) {
            prompt.append("사용자의 일정이 없습니다.\n")
                    .append("아침, 점심, 저녁 시간대를 고려하여 하루 추천 일정을 2~3개 생성해주세요.\n")
                    .append("JSON 형식만 출력해주세요. 설명 없이.\n")
                    .append("```json\n")
                    .append("[\n")
                    .append("  {\"contents\":\"운동\", \"scheduleMonth\":4, \"scheduleDay\":4, \"startTime\":\"07:00\", \"endTime\":\"08:00\"},\n")
                    .append("  {\"contents\":\"스터디\", \"scheduleMonth\":4, \"scheduleDay\":4, \"startTime\":\"10:00\", \"endTime\":\"12:00\"}\n")
                    .append("]\n")
                    .append("```");
        } else {
            prompt.append("아래는 사용자의 일정 목록입니다.\n")
                    .append("빈 시간에 추천 일정을 생성해주세요.\n\n");

            for (Schedules schedule : schedules) {
                prompt.append("- ")
                        .append(schedule.getScheduleMonth()).append("월 ")
                        .append(schedule.getScheduleDay()).append("일 ")
                        .append(schedule.getStartTime().toLocalTime()).append("~")
                        .append(schedule.getEndTime().toLocalTime()).append(" : ")
                        .append(schedule.getContents()).append("\n");
            }

            prompt.append("\n반드시 JSON 형식만 출력해주세요. 설명 절대 금지.\n")
                    .append("```json\n")
                    .append("[{\"contents\":\"운동\", \"scheduleMonth\":4, \"scheduleDay\":4, \"startTime\":\"14:00\", \"endTime\":\"15:00\"}]\n")
                    .append("```");
        }

        return prompt.toString();
    }

    private OpenAiRequest buildOpenAiRequest(String prompt) {
        return OpenAiRequest.builder()
                .model("gpt-4o")
                .messages(List.of(OpenAiRequest.Message.builder()
                        .role("user")
                        .content(prompt)
                        .build()))
                .build();
    }

    private String extractJsonFromContent(String content) {
        Pattern pattern = Pattern.compile("```json\\s*(\\[.*?\\])\\s*```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);  // JSON 덩어리만 추출
        }
        return content;  // 최악의 경우 전체 반환 (예외처리 필요)
    }


    private LocalDateTime mergeDateAndTime(int year, int month, int day, String timeStr) {
        return LocalDateTime.parse(String.format("%04d-%02d-%02dT%s", year, month, day, timeStr));
    }
}
