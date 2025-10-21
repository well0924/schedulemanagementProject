package com.example.inbound.schedules;

import com.example.apimodel.schedule.ScheduleApiModel;
import com.example.schedules.mapper.ScheduleMapper;
import com.example.service.schedule.ScheduleRecommendationService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class ScheduleRecommendationConnectorImpl implements ScheduleRecommendationConnector {

    private final ScheduleRecommendationService scheduleRecommendationService;

    private final ScheduleMapper scheduleMapper;

    @Override
    public Mono<List<ScheduleApiModel.responseSchedule>> recommend(String userId, Pageable pageable) {
        return scheduleRecommendationService.recommendSchedules(userId,pageable)
                .map(schedules -> schedules.stream()
                        .map(scheduleMapper::toApi)
                        .collect(Collectors.toList()));
    }

}
