package com.example.inbound.consumer.member;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.interfaces.notification.kafka.KafkaEventConsumer;
import com.example.notification.model.FailMessageModel;
import com.example.notification.service.FailedMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqMemberSignRetryConsumer implements KafkaEventConsumer<MemberSignUpKafkaEvent> {

    private final FailedMessageService failedMessageService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "member-signup-events.DLQ", groupId = "dlq-retry-group")
    @Override
    public void handle(MemberSignUpKafkaEvent event) {
        log.warn(" DLQ 재처리 (member signup): {}", event);
        try {
            String payload = objectMapper.writeValueAsString(event);
            FailMessageModel failMessageModel = FailMessageModel
                    .builder()
                    .topic("member-signup-events-events")
                    .messageType("MEMBER_SIGNUP")
                    .payload(payload)
                    .retryCount(0)
                    .resolved(false)
                    .exceptionMessage("자동 DLQ 저장")
                    .createdAt(LocalDateTime.now())
                    .build();
            failedMessageService.createFailMessage(failMessageModel);
            log.warn(" DLQ 메시지 저장 완료: {}", payload);
        } catch (Exception e) {
            log.error(" DLQ 메시지 저장 실패", e);
        }
    }

}
