package com.example.inbound.schedules;

import com.example.apimodel.schedule.ScheduleApiModel;
import com.example.model.schedules.SchedulesModel;
import com.example.service.schedule.ScheduleRecommendationService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class ScheduleRecommendationConnectorImpl implements ScheduleRecommendationConnector {

    private final ScheduleRecommendationService scheduleRecommendationService;

    @Override
    public List<ScheduleApiModel.responseSchedule> recommend(String userId, Pageable pageable) throws Exception {
        return scheduleRecommendationService.recommendSchedules(userId,pageable)
                .stream().map(this::toApi).collect(Collectors.toList());
    }

    private ScheduleApiModel.responseSchedule toApi(SchedulesModel schedulesModel){
        return ScheduleApiModel.responseSchedule
                .builder()
                .id(schedulesModel.getId())
                .contents(schedulesModel.getContents())
                .categoryId(schedulesModel.getCategoryId())
                .memberId(schedulesModel.getMemberId())
                .scheduleMonth(schedulesModel.getScheduleMonth())
                .createdTime(schedulesModel.getCreatedTime())
                .updatedTime(schedulesModel.getUpdatedTime())
                .build();
    }
}
