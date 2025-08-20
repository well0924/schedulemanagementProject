package com.example.controller.schedule;

import com.example.apimodel.schedule.ScheduleApiModel;
import com.example.inbound.schedules.ScheduleRecommendationConnectorImpl;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@AllArgsConstructor
public class ScheduleRecommendController {

    private final ScheduleRecommendationConnectorImpl scheduleRecommendationConnector;

    @GetMapping("/recommend")
    public List<ScheduleApiModel.responseSchedule> recommend(@RequestParam(name = "userId") String userId, @PageableDefault Pageable pageable) throws Exception {
        List<ScheduleApiModel.responseSchedule> result = scheduleRecommendationConnector.recommend(userId, pageable).block();
        return result;
    }
}
