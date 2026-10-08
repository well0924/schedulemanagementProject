package com.example.inbound.consumer.chatbot;

import com.example.events.process.ProcessedEventService;
import com.example.events.spring.ChatCompletedEvent;
import com.example.inbound.schedules.ScheduleRecommendationCachePort;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.interfaces.category.CategoryRepositoryPort;
import com.example.interfaces.notification.kafka.KafkaEventConsumer;
import com.example.logging.MDC.KafkaMDCUtil;
import com.example.model.schedules.CategoryFrequency;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatternAnalysisConsume implements KafkaEventConsumer<ChatCompletedEvent> {

    // 중복 처리 판단 단위. 리스너 groupId와 같은 값이어야 한다.
    private static final String CONSUMER = "pattern-analysis";

    private final ScheduleRecommendationCachePort scheduleRecommendationCachePort;
    private final ScheduleRepositoryPort scheduleRepositoryPort;
    private final CategoryRepositoryPort categoryRepositoryPort;
    private final ProcessedEventService processedEventService;

    @Timed(value = "kafka.consumer.chat.pattern.time", description = "사용자 패턴 분석 소요 시간")
    @Counted(value = "kafak.consumer.chat.pattern.count", description = "사용자 패턴 처리 횟수")
    @KafkaListener(
            topics = "chat-history",
            groupId = CONSUMER,  // groupId 다르면 같은 토픽 독립 소비
            containerFactory = "chatKafkaListenerFactory"
    )
    @Override
    public void handle(ChatCompletedEvent event, Acknowledgment ack) {
        log.info("[HistorySaveConsumer] memberId={}", event.getMemberId());

        if (processedEventService.isAlreadyProcessed(CONSUMER, event.getEventId())) {
            log.info("⚠️ 이미 처리된 이벤트 (Skip): {}", event.getEventId());
            ack.acknowledge(); // 중복은 성공으로 간주하고 넘김
            return;
        }

        try {
            KafkaMDCUtil.initMDC(event);

            // 핵심 분석 로직들을 private 메서드로 격리
            // 1. 시간대 선호도 누적 분석
            analyzeTimePreference(event.getMemberId(), event.getCreatedAt());

            // 2. 대화 키워드 기반 관심사 추출
            analyzeMessageContext(event.getMemberId(), event.getUserMessage());

            // 분석이 끝난 뒤에 처리 기록을 남긴다. 먼저 남기면 분석이 실패해도 재시도가 건너뛴다.
            // (분석 결과는 Redis 누적이라, 기록 직전에 서버가 죽으면 재처리로 한 번 더 누적될 수 있다)
            processedEventService.saveProcessedEvent(CONSUMER, event.getEventId());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("[ChatPatternAnalysisConsumer] 실패: {}", e.getMessage(), e);
            throw e;
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    private void analyzeTimePreference(Long memberId, LocalDateTime chatTime) {
        String key = "pattern:time:" + memberId;
        int hour = chatTime.getHour();

        // 단순히 set이 아니라, 데이터가 누적되는 구조임을 암시 (예: Redis HINCRBY)
        if (hour < 12) {
            scheduleRecommendationCachePort.increment(key, "morning", 1);
        } else {
            scheduleRecommendationCachePort.increment(key, "afternoon", 1);
        }
    }

    private void analyzeMessageContext(Long memberId, String message) {
        String key = "pattern:interest:" + memberId;

        // 사용자가 실제 쓰는 카테고리 이름이 메시지에 등장하면 해당 카테고리를 관심사로 누적
        // (카테고리 이름 조회는 CategoryOutConnector에서 이미 캐싱되어 있어 매번 DB를 타지 않음)
        List<CategoryFrequency> frequencies = scheduleRepositoryPort.countByCategoryForMember(memberId);
        for (CategoryFrequency frequency : frequencies) {
            String categoryName = resolveCategoryName(frequency.categoryId());
            if (categoryName != null && message.contains(categoryName)) {
                scheduleRecommendationCachePort.increment(key, categoryName, 1);
            }
        }
    }

    private String resolveCategoryName(Long categoryId) {
        try {
            return categoryRepositoryPort.findById(categoryId).getName();
        } catch (Exception e) {
            log.warn("[PatternAnalysisConsume] 카테고리 조회 실패, 건너뜀 - categoryId={}, reason={}", categoryId, e.getMessage());
            return null;
        }
    }
}
