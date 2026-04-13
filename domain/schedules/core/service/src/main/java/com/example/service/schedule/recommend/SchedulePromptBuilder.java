package com.example.service.schedule.recommend;

import com.example.interfaces.category.CategoryRepositoryPort;
import com.example.outbound.openai.dto.TimeSlot;
import com.example.rdbrepository.Schedules;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@AllArgsConstructor
public class SchedulePromptBuilder {

    private final CategoryRepositoryPort categoryRepository;

    /**
     * 회원의 일정 목록을 기반으로 OpenAI 프롬프트를 생성한다.
     */
    public String build(List<Schedules> schedules) {
        StringBuilder prompt = new StringBuilder();
        LocalDate baseDate = LocalDate.now();

        if (schedules.isEmpty()) {
            return buildEmptyPrompt(prompt, baseDate);
        }

        baseDate = schedules.get(0).getStartTime().toLocalDate();

        appendCategoryAnalysis(prompt, schedules);
        appendContentsAnalysis(prompt, schedules);
        appendScheduleList(prompt, schedules, baseDate);
        appendAvailableSlots(prompt, schedules);
        appendTimeAnalysis(prompt, schedules);
        appendOverloadAnalysis(prompt, schedules);
        appendJsonFormat(prompt, baseDate);

        return prompt.toString();
    }

    /**
     * 카테고리별 빈도를 분석하여 프롬프트에 추가한다.
     */
    private void appendCategoryAnalysis(StringBuilder prompt, List<Schedules> schedules) {
        Map<Long, String> categoryNameMap = buildCategoryNameMap(schedules);
        Map<String, Long> categoryFrequency = buildCategoryFrequency(schedules, categoryNameMap);
        String topCategory = findTopCategory(categoryFrequency);

        appendTopCategory(prompt, topCategory);
        appendCategoryDistribution(prompt, categoryFrequency);
    }

    /**
     * categoryId → 카테고리 이름 맵을 생성한다.
     */
    private Map<Long, String> buildCategoryNameMap(List<Schedules> schedules) {
        return schedules
                .stream()
                .map(Schedules::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> categoryRepository.existsById(id)
                                ? categoryRepository.findById(id).getName()
                                : "미분류"
                ));
    }

    /**
     * 카테고리별 일정 빈도를 계산한다.
     */
    private Map<String, Long> buildCategoryFrequency(
            List<Schedules> schedules, Map<Long, String> categoryNameMap) {
        return schedules
                .stream()
                .filter(s -> s.getCategoryId() != null)
                .collect(Collectors.groupingBy(
                        s -> categoryNameMap.getOrDefault(s.getCategoryId(), "미분류"),
                        Collectors.counting()
                ));
    }

    /**
     * 가장 많이 사용한 카테고리를 반환한다.
     */
    private String findTopCategory(Map<String, Long> categoryFrequency) {
        return categoryFrequency
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * 가장 많이 사용한 카테고리를 프롬프트에 추가한다.
     */
    private void appendTopCategory(StringBuilder prompt, String topCategory) {
        if (topCategory != null) {
            prompt.append("사용자가 가장 많이 사용하는 카테고리는 '")
                    .append(topCategory)
                    .append("' 입니다. 이 카테고리와 관련된 일정을 우선 추천해주세요.\n");
        }
    }

    /**
     * 카테고리별 분포를 프롬프트에 추가한다.
     */
    private void appendCategoryDistribution(
            StringBuilder prompt, Map<String, Long> categoryFrequency) {
        if (!categoryFrequency.isEmpty()) {
            prompt.append("카테고리별 일정 분포: ");
            categoryFrequency.forEach((category, count) ->
                    prompt
                            .append(category)
                            .append("(")
                            .append(count)
                            .append("건) "));
            prompt.append("\n");
        }
    }

    /**
     * 일정 내용 빈도를 분석하여 자주 하는 활동을 프롬프트에 추가한다.
     */
    private void appendContentsAnalysis(StringBuilder prompt, List<Schedules> schedules) {
        String topContent = findTopContent(schedules);

        if (topContent != null && !topContent.isBlank()) {
            prompt.append("사용자는 '").append(topContent)
                    .append("' 활동을 자주 합니다. 이와 관련된 추천 일정을 포함해주세요.\n");
        }
    }

    /**
     * 가장 많이 등록한 일정 내용을 반환한다.
     */
    private String findTopContent(List<Schedules> schedules) {
        return schedules.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getContents() != null ? s.getContents() : "",
                        Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * 일정 목록을 프롬프트에 추가한다.
     */
    private void appendScheduleList(StringBuilder prompt, List<Schedules> schedules, LocalDate baseDate) {
        prompt.append("아래는 사용자의 ").append(baseDate).append(" 일정 목록입니다.\n");

        schedules.forEach(schedule -> {
            String contents = schedule.getContents() != null ? schedule.getContents() : "";
            LocalDate date = schedule.getStartTime().toLocalDate();
            prompt.append("- ")
                    .append(date).append(" ")
                    .append(schedule.getStartTime().toLocalTime()).append("~")
                    .append(schedule.getEndTime().toLocalTime()).append(" : ")
                    .append(contents).append("\n");
        });
    }

    /**
     * 일정 사이 빈 시간대를 분석하여 프롬프트에 추가한다.
     */
    private void appendAvailableSlots(StringBuilder prompt, List<Schedules> schedules) {
        List<TimeSlot> timeSlots = findAvailableTimeSlots(schedules, Duration.ofMinutes(30));

        if (!timeSlots.isEmpty()) {
            prompt.append("\n사용자의 일정 사이 빈 시간은 다음과 같습니다:\n");
            timeSlots.forEach(slot ->
                    prompt.append("- ")
                            .append(slot.start().toLocalTime()).append(" ~ ")
                            .append(slot.end().toLocalTime()).append("\n"));
            prompt.append("이 시간대에 적절한 일정을 추천해주세요.\n");
        }
    }

    /**
     * 30분 이상의 빈 시간대를 찾는다.
     */
    private List<TimeSlot> findAvailableTimeSlots(
            List<Schedules> schedules, Duration minimumGap) {
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

    /**
     * 아침/저녁 일정 여부를 분석하여 프롬프트에 추가한다.
     */
    private void appendTimeAnalysis(StringBuilder prompt, List<Schedules> schedules) {
        boolean hasMorning = hasMorningSchedule(schedules);
        boolean hasEvening = hasEveningSchedule(schedules);

        if (!hasMorning) prompt.append("사용자는 아침 일정이 없습니다. 아침 추천을 포함해주세요.\n");
        if (!hasEvening) prompt.append("사용자는 저녁 일정이 없습니다. 저녁 추천을 포함해주세요.\n");
    }

    /**
     * 10시 이전 일정이 있는지 확인한다.
     */
    private boolean hasMorningSchedule(List<Schedules> schedules) {
        return schedules.stream()
                .anyMatch(s -> s.getStartTime().getHour() < 10);
    }

    /**
     * 18시 이후 일정이 있는지 확인한다.
     */
    private boolean hasEveningSchedule(List<Schedules> schedules) {
        return schedules.stream()
                .anyMatch(s -> s.getStartTime().getHour() >= 18);
    }

    /**
     * 일정 과부하 여부를 분석하여 프롬프트에 추가한다.
     */
    private void appendOverloadAnalysis(StringBuilder prompt, List<Schedules> schedules) {
        appendCountOverload(prompt, schedules);
        appendDurationOverload(prompt, schedules);
    }

    /**
     * 일정 수가 10개 이상이면 과부하 메시지를 추가한다.
     */
    private void appendCountOverload(StringBuilder prompt, List<Schedules> schedules) {
        if (schedules.size() >= 10) {
            prompt.append("사용자의 일정이 많습니다. 여유 일정을 추천해주세요.\n");
        }
    }

    /**
     * 총 일정 시간이 6시간 이상이면 휴식 추천 메시지를 추가한다.
     */
    private void appendDurationOverload(StringBuilder prompt, List<Schedules> schedules) {
        long totalMinutes = schedules.stream()
                .mapToLong(s -> Duration.between(s.getStartTime(), s.getEndTime()).toMinutes())
                .sum();

        if (totalMinutes >= 360) {
            prompt.append("총 일정 시간이 6시간 이상입니다. 휴식 위주 일정도 고려해주세요.\n");
        }
    }

    /**
     * 일정이 없을 때 기본 프롬프트를 생성한다.
     */
    private String buildEmptyPrompt(StringBuilder prompt, LocalDate baseDate) {
        prompt.append("사용자의 일정이 없습니다. 오늘 하루를 알차게 보낼 수 있는 ")
                .append("생산적인 활동 3가지와 휴식 2가지를 포함하여 총 5개의 일정을 추천해 주세요.\n")
                .append("추천 일정은 하루 전체 시간대를 고려하여 균형 있게 구성해주세요.\n")
                .append("각 활동의 소요 시간은 1시간을 넘지 않도록 해주세요.\n");
        appendJsonFormat(prompt, baseDate);
        return prompt.toString();
    }

    /**
     * JSON 형식 안내를 프롬프트에 추가한다.
     */
    private void appendJsonFormat(StringBuilder prompt, LocalDate baseDate) {
        prompt.append("\n응답은 항상 JSON 배열 형식으로만 제공해야 합니다. ")
                .append("설명이나 다른 텍스트는 절대 포함하지 마세요. ")
                .append("각 객체는 반드시 \"contents\", \"startTime\", \"endTime\" 필드를 포함해야 합니다. ")
                .append("\"startTime\"과 \"endTime\"은 \"yyyy-MM-dd'T'HH:mm\" 형식의 문자열이어야 합니다.\n")
                .append("```json\n[\n  {\"contents\": \"운동\", \"startTime\": \"")
                .append(baseDate).append("T14:00\", \"endTime\": \"")
                .append(baseDate).append("T15:00\"}\n]\n```");
    }
}
