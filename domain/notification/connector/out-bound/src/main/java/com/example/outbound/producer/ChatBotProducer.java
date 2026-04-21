package com.example.outbound.producer;

import com.example.events.spring.ChatCompletedEvent;
import com.example.interfaces.notification.chatbot.ChatEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatBotProducer implements ChatEventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "chat-history";

    @Override
    public void handle(ChatCompletedEvent handle) {
        kafkaTemplate.send(TOPIC, String.valueOf(handle.getMemberId()), handle)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka 발행 실패] memberId={}, 이유={}",
                                handle.getMemberId(), ex.getMessage());
                    } else {
                        log.info("[Kafka 발행 성공] memberId={}, offset={}",
                                handle.getMemberId(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
