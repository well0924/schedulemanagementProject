package com.example.schedules.mapper;

import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.model.schedules.SchedulesModel;
import com.example.outbound.openai.dto.ScheduleRecommendationDto;
import com.example.rdbrepository.Schedules;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@AllArgsConstructor
public class ScheduleRecommendMapper {

    private final ObjectMapper objectMapper;

    /**
     * SchedulesModel 리스트를 Schedules 엔티티 리스트로 변환한다.
     * 프롬프트 생성 시 엔티티 구조가 필요하기 때문에 변환한다.
     */
    public List<Schedules> toEntityList(List<SchedulesModel> models) {
        return models.stream()
                .map(m -> Schedules.builder()
                        .id(m.getId())
                        .contents(Optional.ofNullable(m.getContents()).orElse(""))
                        .scheduleMonth(m.getScheduleMonth())
                        .scheduleDay(m.getScheduleDays())
                        .memberId(m.getMemberId())
                        .categoryId(m.getCategoryId())
                        .startTime(m.getStartTime())
                        .endTime(m.getEndTime())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * AI 추천 결과 DTO 리스트를 SchedulesModel 리스트로 변환한다.
     * contents가 List 또는 String으로 올 수 있어 타입 방어 처리를 한다.
     */
    public List<SchedulesModel> toModelList(List<ScheduleRecommendationDto> dtoList, Long memberId) {
        List<SchedulesModel> result = new ArrayList<>();

        for (ScheduleRecommendationDto dto : dtoList) {
            String contents = resolveContents(dto.getContents());
            Integer scheduleMonth = dto.getStartTime() != null
                    ? dto.getStartTime().getMonthValue() : null;
            Integer scheduleDay = dto.getStartTime() != null
                    ? dto.getStartTime().getDayOfMonth() : null;

            result.add(SchedulesModel.builder()
                    .contents(contents)
                    .scheduleMonth(scheduleMonth)
                    .scheduleDays(scheduleDay)
                    .startTime(dto.getStartTime())
                    .endTime(dto.getEndTime())
                    .progressStatus(PROGRESS_STATUS.IN_COMPLETE)
                    .memberId(memberId)
                    .build());
        }
        return result;
    }

    /**
     * AI 응답 JSON 문자열에서 JSON 배열을 추출한다.
     */
    public String extractJson(String content) {
        int startIndex = content.indexOf('[');
        int endIndex = content.lastIndexOf(']');

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return content.substring(startIndex, endIndex + 1);
        }
        log.warn("AI 응답에서 JSON 배열을 추출하지 못했습니다: {}", content);
        return "[]";
    }

    /**
     * JSON 문자열을 ScheduleRecommendationDto 리스트로 파싱한다.
     */
    public List<ScheduleRecommendationDto> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("AI 응답 파싱 중 오류", e);
        }
    }

    /**
     * contents 필드가 List 또는 String으로 올 수 있어 타입 방어 처리한다.
     */
    private String resolveContents(Object rawContents) {
        if (rawContents instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> listContents = (List<String>) rawContents;
            return String.join(",", listContents);
        } else if (rawContents != null) {
            return rawContents.toString();
        }
        return "";
    }
}
