package com.example.schedule.openapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleRecommendationDto {
    private String contents;
    private Integer scheduleMonth;
    private Integer scheduleDay;
    private String startTime;
    private String endTime;
}
