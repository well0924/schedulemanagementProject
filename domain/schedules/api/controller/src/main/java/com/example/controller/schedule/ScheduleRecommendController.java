package com.example.controller.schedule;

import com.example.apimodel.schedule.ScheduleApiModel;
import com.example.inbound.schedules.ScheduleRecommendationConnector;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@AllArgsConstructor
public class ScheduleRecommendController {

    private final ScheduleRecommendationConnector scheduleRecommendationConnector;

    @GetMapping("/recommend")
    public List<ScheduleApiModel.responseSchedule> recommend(@RequestHeader("Authorization") String accessToken,
                                                             @PageableDefault Pageable pageable) {
        List<ScheduleApiModel.responseSchedule> result = scheduleRecommendationConnector.recommend(accessToken, pageable).block();
        return result;
    }
}
