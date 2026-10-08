package com.example.inbound.consumer.chatbot;

import com.example.events.process.ProcessedEventService;
import com.example.events.spring.ChatCompletedEvent;
import com.example.inbound.schedules.ChatHistoryPort;
import com.example.inbound.schedules.ScheduleRecommendationCachePort;
import com.example.interfaces.notification.kafka.KafkaEventConsumer;
import com.example.logging.MDC.KafkaMDCUtil;
import com.example.model.schedules.ChatHistoryModel;
import com.example.outbound.openai.dto.ChatMessage;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHistorySaveConsumer implements KafkaEventConsumer<ChatCompletedEvent> {

    // 중복 처리 판단 단위. 리스너 groupId와 같은 값이어야 한다.
    private static final String CONSUMER = "chat-history-save";

    private final ChatHistoryPort chatHistoryRepository;
    private final ScheduleRecommendationCachePort cacheService;
    private final ProcessedEventService processedEventService;
    private final TransactionTemplate transactionTemplate;

    @Timed(value = "kafka.consumer.chat.save.time", description = "이력 저장 소요 시간")
    @Counted(value = "kafka.consumer.chat.save.count", description = "이력 저장 처리 횟수")
    @KafkaListener(
            topics = "chat-history",
            groupId = CONSUMER,
            containerFactory = "chatKafkaListenerFactory"
    )
    @Override
    public void handle(ChatCompletedEvent event, Acknowledgment ack) {
        log.info("[ChatHistorySaveConsumer] memberId={}", event.getMemberId());

        if (processedEventService.isAlreadyProcessed(CONSUMER, event.getEventId())) {
            log.info("⚠️ 이미 처리된 이벤트 (Skip): {}", event.getEventId());
            ack.acknowledge(); // 중복은 성공으로 간주하고 넘김
            return;
        }

        try {
            KafkaMDCUtil.initMDC(event);

            // MySQL 영구 저장 + 처리 기록 (같은 트랜잭션).
            // 처리 기록을 먼저 따로 커밋하면 이력 저장이 실패해도 재시도가 "처리됨"으로 건너뛴다.
            transactionTemplate.executeWithoutResult(status -> {
                chatHistoryRepository.save(ChatHistoryModel.builder()
                        .memberId(event.getMemberId())
                        .userMessage(event.getUserMessage())
                        .assistantResponse(event.getAssistantResponse())
                        .createdAt(event.getCreatedAt())
                        .build());
                processedEventService.saveProcessedEvent(CONSUMER, event.getEventId());
            });

            // 커밋 후: Redis 이력 갱신(다음 대화 맥락용) → 오프셋 커밋
            appendToChatContext(event);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("[ChatHistorySaveConsumer] 실패: {}", e.getMessage(), e);
            throw e;  // DLQ로 이동
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    // Redis 맥락은 캐시라 실패해도 재시도하지 않는다 (이력은 MySQL에 저장됨)
    private void appendToChatContext(ChatCompletedEvent event) {
        try {
            cacheService.appendChatMessage(event.getMemberId(),
                    ChatMessage
                            .builder()
                            .role("user")
                            .content(event.getUserMessage())
                            .createdAt(event.getCreatedAt())
                            .build());

            cacheService.appendChatMessage(event.getMemberId(),
                    ChatMessage
                            .builder()
                            .role("assistant")
                            .content(event.getAssistantResponse())
                            .createdAt(event.getCreatedAt())
                            .build());
        } catch (Exception e) {
            log.error("[ChatHistorySaveConsumer] Redis 맥락 갱신 실패 (이력은 저장됨): memberId={}", event.getMemberId(), e);
        }
    }
}
