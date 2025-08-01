package com.example.service.schedule;

import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.model.schedules.SchedulesModel;
import com.example.outbound.openai.config.OpenAiWebClient;
import com.example.outbound.openai.dto.OpenAiRequest;
import com.example.outbound.openai.dto.OpenAiResponse;
import com.example.outbound.openai.dto.ScheduleRecommendationDto;
import com.example.rdb.member.MemberRepository;
import com.example.rdbrepository.ScheduleRepository;
import com.example.rdbrepository.Schedules;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@AllArgsConstructor
public class ScheduleRecommendationService {

    private final ScheduleRepository schedulesRepository;

    private final MemberRepository memberRepository;

    private final OpenAiWebClient aiClient;

    private final ObjectMapper objectMapper;

    private static final Pattern pattern = Pattern.compile("```json\\s*(\\[.*?\\])\\s*```", Pattern.DOTALL);

    @CircuitBreaker(name = "openAiClient", fallbackMethod = "fallbackRecommendSchedules")
    public List<SchedulesModel> recommendSchedules(String userId, Pageable pageable) throws Exception {
        try{
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
        } catch (JsonProcessingException e) {
            throw new RuntimeException("AI 응답 파싱 중 오류 발생: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("일정 추천 중 알 수 없는 오류 발생: " + e.getMessage(), e);
        }
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

    private List<SchedulesModel> mapToRecommendedSchedules(List<ScheduleRecommendationDto> dtoList, Long memberId) {
        List<SchedulesModel> recommendedSchedules = new ArrayList<>();

        for (ScheduleRecommendationDto dto : dtoList) {

            recommendedSchedules.add(SchedulesModel.builder()
                    .contents(dto.getContents())
                    .scheduleMonth(dto.getScheduleMonth())
                            .scheduleDays(dto.getScheduleDay())
                    .startTime(dto.getStartTime())
                    .endTime(dto.getEndTime())
                    .progressStatus(PROGRESS_STATUS.IN_COMPLETE)
                    .userId(memberId)
                    .build());
        }

        return recommendedSchedules;
    }

    private String buildPromptFromSchedules(List<Schedules> schedules) {
        StringBuilder prompt = new StringBuilder();

        if (schedules.isEmpty()) {
            LocalDate now = LocalDate.now();
            String today = now.toString(); // 예: 2025-05-05

            prompt.append("사용자의 일정이 없습니다.\n")
                    .append("오늘 날짜는 ").append(today).append("입니다.\n")
                    .append("아침, 점심, 저녁 시간대를 고려하여 하루 추천 일정을 2~3개 생성해주세요.\n")
                    .append("모든 시간은 반드시 'yyyy-MM-ddTHH:mm' 형식으로 작성해주세요.\n")
                    .append("JSON 형식만 출력해주세요. 설명 없이.\n")
                    .append("```json\n")
                    .append("[\n")
                    .append("  {\"contents\":\"운동\", \"startTime\":\"").append(today).append("T07:00\", \"endTime\":\"").append(today).append("T08:00\"},\n")
                    .append("  {\"contents\":\"스터디\", \"startTime\":\"").append(today).append("T10:00\", \"endTime\":\"").append(today).append("T12:00\"}\n")
                    .append("]\n")
                    .append("```");
        } else {
            // 사용자의 첫 일정 기준으로 날짜를 추출 (모든 일정이 동일 날짜라는 전제)
            Schedules first = schedules.get(0);
            LocalDate baseDate = LocalDate.of(2025, first.getScheduleMonth(), first.getScheduleDay());

            prompt.append("아래는 사용자의 일정 목록입니다. 날짜는 ").append(baseDate).append("입니다.\n")
                    .append("해당 일자의 빈 시간에 추천 일정을 생성해주세요.\n")
                    .append("모든 시간은 반드시 'yyyy-MM-ddTHH:mm' 형식으로 작성해주세요.\n\n");

            for (Schedules schedule : schedules) {
                LocalDate date = LocalDate.of(2025, schedule.getScheduleMonth(), schedule.getScheduleDay());
                prompt.append("- ")
                        .append(date).append(" ")
                        .append(schedule.getStartTime().toLocalTime()).append("~")
                        .append(schedule.getEndTime().toLocalTime()).append(" : ")
                        .append(schedule.getContents()).append("\n");
            }

            // 예시에서도 baseDate 사용
            prompt.append("\nJSON 형식만 출력해주세요. 설명 절대 금지.\n")
                    .append("```json\n")
                    .append("[\n")
                    .append("  {\"contents\":\"운동\", \"startTime\":\"").append(baseDate).append("T14:00\", \"endTime\":\"").append(baseDate).append("T15:00\"}\n")
                    .append("]\n")
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

        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);  // JSON 덩어리만 추출
        }
        return content;  // 최악의 경우 전체 반환 (예외처리 필요)
    }
}
