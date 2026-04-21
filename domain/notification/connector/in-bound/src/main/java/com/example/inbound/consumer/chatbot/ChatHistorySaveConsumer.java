package com.example.inbound.consumer.chatbot;

import com.example.events.process.ProcessedEventService;
import com.example.events.spring.ChatCompletedEvent;
import com.example.inbound.schedules.ChatHistoryPort;
import com.example.inbound.schedules.ScheduleRecommendationCachePort;
import com.example.interfaces.notification.kafka.KafkaEventConsumer;
import com.example.logging.MDC.KafkaMDCUtil;
import com.example.model.schedules.ChatHistoryModel;
import com.example.outbound.openai.dto.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHistorySaveConsumer implements KafkaEventConsumer<ChatCompletedEvent> {

    private final ChatHistoryPort chatHistoryRepository;
    private final ScheduleRecommendationCachePort cacheService;
    private final ProcessedEventService processedEventService;

    @KafkaListener(
            topics = "chat-history",
            groupId = "chat-history-save",
            containerFactory = "chatKafkaListenerFactory"
    )
    @Override
    @Transactional
    public void handle(ChatCompletedEvent event, Acknowledgment ack) {
        log.info("[ChatHistorySaveConsumer] memberId={}", event.getMemberId());

        if (processedEventService.isAlreadyProcessed(event.getEventId())) {
            log.info("⚠️ 이미 처리된 이벤트 (Skip): {}", event.getEventId());
            ack.acknowledge(); // 중복은 성공으로 간주하고 넘김
            return;
        }

        try {
            KafkaMDCUtil.initMDC(event);

            processedEventService.saveProcessedEvent(event.getEventId());
            // MySQL 영구 저장
            chatHistoryRepository.save(ChatHistoryModel.builder()
                    .memberId(event.getMemberId())
                    .userMessage(event.getUserMessage())
                    .assistantResponse(event.getAssistantResponse())
                    .createdAt(event.getCreatedAt())
                    .build());

            // Redis 이력 갱신 (다음 대화 맥락용)
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

            ack.acknowledge();  // 수동 커밋

        } catch (DataIntegrityViolationException e) {
            // 이미 처리된 이벤트라면 무시
            log.warn("이벤트 중복 저장 시도 감지됨: {}", event.getEventId());
        } catch (Exception e) {
            log.error("[ChatHistorySaveConsumer] 실패: {}", e.getMessage(), e);
            throw e;  // DLQ로 이동
        } finally {
            KafkaMDCUtil.clear();
        }
    }
}
