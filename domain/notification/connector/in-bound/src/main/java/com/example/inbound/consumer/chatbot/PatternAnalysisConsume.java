package com.example.inbound.consumer.chatbot;

import com.example.events.process.ProcessedEventService;
import com.example.events.spring.ChatCompletedEvent;
import com.example.inbound.schedules.ScheduleRecommendationCachePort;
import com.example.interfaces.notification.kafka.KafkaEventConsumer;
import com.example.logging.MDC.KafkaMDCUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatternAnalysisConsume implements KafkaEventConsumer<ChatCompletedEvent> {


    private final ScheduleRecommendationCachePort scheduleRecommendationCachePort;
    private final ProcessedEventService processedEventService;

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
            String userMsg = event.getUserMessage().toLowerCase();
            String patternKey = "chat:pattern:" + event.getMemberId();

            if (userMsg.contains("오전") || userMsg.contains("아침")) {
                scheduleRecommendationCachePort.set(patternKey + ":time", "morning", Duration.ofDays(7));
            } else if (userMsg.contains("오후") || userMsg.contains("저녁")) {
                scheduleRecommendationCachePort.set(patternKey + ":time", "afternoon", Duration.ofDays(7));
            }

            ack.acknowledge();

        } catch (DataIntegrityViolationException e) {
            // 이미 처리된 이벤트라면 무시
            log.warn("이벤트 중복 저장 시도 감지됨: {}", event.getEventId());
        } catch (Exception e) {
            log.error("[ChatPatternAnalysisConsumer] 실패: {}", e.getMessage(), e);
            throw e;
        }
    }
}
