package com.example.inbound.schedules;

import com.example.apimodel.schedule.ScheduleApiModel;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ScheduleRecommendationConnector {

    Mono<List<ScheduleApiModel.responseSchedule>> recommend(String accessToken, Pageable pageable);

    Flux<String> streamChat(Long memberId, String userMessage);
}
