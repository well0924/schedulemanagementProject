package com.example.inbound.schedules;

import com.example.apimodel.schedule.ScheduleApiModel;
import com.example.model.schedules.SchedulesModel;
import com.example.service.schedule.ScheduleRecommendationService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class ScheduleRecommendationConnectorImpl implements ScheduleRecommendationConnector {

    private final ScheduleRecommendationService scheduleRecommendationService;

    private static final long DEFAULT_ID = -1L;

    @Override
    public Mono<List<ScheduleApiModel.responseSchedule>> recommend(String userId, Pageable pageable) {
        return scheduleRecommendationService.recommendSchedules(userId,pageable)
                .map(schedules -> schedules.stream()
                        .map(this::toApi)
                        .collect(Collectors.toList()));
    }

    private ScheduleApiModel.responseSchedule toApi(SchedulesModel schedulesModel){
        return ScheduleApiModel.responseSchedule
                .builder()
                .id(Optional.ofNullable(schedulesModel.getId()).orElse(DEFAULT_ID))
                .contents(schedulesModel.getContents())
                .categoryId(Optional.ofNullable(schedulesModel.getCategoryId()).orElse(DEFAULT_ID))
                .memberId(schedulesModel.getMemberId())
                .scheduleMonth(schedulesModel.getScheduleMonth())
                .scheduleDays(schedulesModel.getScheduleDays())
                .startTime(schedulesModel.getStartTime())
                .endTime(schedulesModel.getEndTime())
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();
    }
}
