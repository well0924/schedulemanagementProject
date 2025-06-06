package com.example.notification.service;

import com.example.events.enums.AggregateType;
import com.example.events.enums.EventType;
import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.outbox.OutboxEventService;
import com.example.events.spring.MemberSignUpEvent;
import com.example.interfaces.notification.event.NotificationEventInterfaces;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberEventListener implements NotificationEventInterfaces<MemberSignUpEvent> {

    private final OutboxEventService outboxEventService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Override
    public void handle(MemberSignUpEvent handle) {

        MemberSignUpKafkaEvent memberSignUpKafkaEvent = MemberSignUpKafkaEvent
                .of(handle.getMemberId(), handle.getUsername(), handle.getEmail());

        outboxEventService.saveEvent(
                memberSignUpKafkaEvent,
                AggregateType.MEMBER.name(),
                handle.getMemberId().toString(),
                EventType.SIGNED_UP_WELCOME.name()
        );
    }
}
