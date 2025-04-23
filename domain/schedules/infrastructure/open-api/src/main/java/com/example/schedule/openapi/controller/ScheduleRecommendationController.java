package com.example.schedule.openapi.controller;

import com.example.rdbrepository.Schedules;
import com.example.schedule.openapi.service.ScheduleRecommendService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/chat")
public class ScheduleRecommendationController {

    private final ScheduleRecommendService recommendService;

    @GetMapping("/recommend")
    public ResponseEntity<List<Schedules>> recommend(@RequestParam(name = "userId") String userId, @PageableDefault Pageable pageable) throws Exception {
        List<Schedules> result = recommendService.recommendSchedules(userId, pageable);
        return ResponseEntity.ok(result);
    }
}
