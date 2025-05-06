package com.example.inbound.schedules;

import com.example.apimodel.schedule.ScheduleApiModel;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ScheduleRecommendationConnector {

    List<ScheduleApiModel.responseSchedule> recommend(String userId, Pageable pageable) throws Exception;
}
