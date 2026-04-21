package com.example.outbound.schedule;

import com.example.events.enums.AggregateType;
import com.example.events.enums.EventType;
import com.example.events.outbox.OutboxEventService;
import com.example.events.spring.ChatCompletedEvent;
import com.example.interfaces.notification.chatbot.ChatEventPort;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ChatOutboxAdapter implements ChatEventPort {

    private final OutboxEventService outboxEventService;

    @Override
    public void publish(ChatCompletedEvent event) {
        outboxEventService.saveEvent(
                event,
                AggregateType.CHAT.name(),
                event.getMemberId().toString(),
                EventType.CHAT_COMPLETED.name()
        );
    }

    @Override
    public void handle(ChatCompletedEvent handle) {

    }
}
