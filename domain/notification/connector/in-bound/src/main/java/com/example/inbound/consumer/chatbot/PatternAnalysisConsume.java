package com.example.inbound.consumer.chatbot;

import com.example.events.process.ProcessedEventService;
import com.example.events.spring.ChatCompletedEvent;
import com.example.inbound.schedules.ScheduleRecommendationCachePort;
import com.example.interfaces.notification.kafka.KafkaEventConsumer;
import com.example.logging.MDC.KafkaMDCUtil;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatternAnalysisConsume implements KafkaEventConsumer<ChatCompletedEvent> {


    private final ScheduleRecommendationCachePort scheduleRecommendationCachePort;
    private final ProcessedEventService processedEventService;

    @Timed(value = "kafka.consumer.chat.pattern.time", description = "사용자 패턴 분석 소요 시간")
    @Counted(value = "kafak.consumer.chat.pattern.count", description = "사용자 패턴 처리 횟수")
    @KafkaListener(
            topics = "chat-history",
            groupId = "pattern-analysis",  // groupId 다르면 같은 토픽 독립 소비
            containerFactory = "chatKafkaListenerFactory"
    )
    @Override
    public void handle(ChatCompletedEvent event, Acknowledgment ack) {
        log.info("[HistorySaveConsumer] memberId={}", event.getMemberId());

        if (processedEventService.isAlreadyProcessed(event.getEventId())) {
            log.info("⚠️ 이미 처리된 이벤트 (Skip): {}", event.getEventId());
            ack.acknowledge(); // 중복은 성공으로 간주하고 넘김
            return;
        }

        try {
            KafkaMDCUtil.initMDC(event);
            processedEventService.saveProcessedEvent(event.getEventId());

            // 핵심 분석 로직들을 private 메서드로 격리
            // 1. 시간대 선호도 누적 분석
            analyzeTimePreference(event.getMemberId(), event.getCreatedAt());

            // 2. 대화 키워드 기반 관심사 추출
            analyzeMessageContext(event.getMemberId(), event.getUserMessage());

            ack.acknowledge();

        } catch (DataIntegrityViolationException e) {
            // 이미 처리된 이벤트라면 무시
            log.warn("이벤트 중복 저장 시도 감지됨: {}", event.getEventId());
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

        // TODO: 이후 NLP(자연어 처리) 라이브러리나 외부 API 연동 지점
        if (message.contains("운동") || message.contains("헬스")) {
            scheduleRecommendationCachePort.increment(key, "health", 1);
        } else if (message.contains("공부") || message.contains("독서")) {
            scheduleRecommendationCachePort.increment(key, "study", 1);
        }
    }
}
