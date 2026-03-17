package com.example.inbound.consumer.member;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.interfaces.notification.kafka.KafkaEventConsumer;
import com.example.logging.MDC.KafkaMDCUtil;
import com.example.notification.model.FailMessageModel;
import com.example.notification.service.FailedMessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqMemberSignRetryConsumer implements KafkaEventConsumer<MemberSignUpKafkaEvent> {

    private final FailedMessageService failedMessageService;
    private final ObjectMapper objectMapper;

    @Timed(value = "kafka.dlq.signup.save.duration", description = "회원가입 DLQ 저장 처리 시간")
    @KafkaListener(topics = "member-signup-events.DLQ", groupId = "dlq-retry-group")
    @Override
    public void handle(MemberSignUpKafkaEvent event, Acknowledgment acknowledgment) {
        log.warn(" DLQ 재처리 (member signup): {}", event);
        try {
            KafkaMDCUtil.initMDC(event);
            saveToFail(event);
            // 실패 내역 저장성공시 카프카에 커밋
            acknowledgment.acknowledge(); 
        } catch (Exception e) {
            log.error(" DLQ 메시지 저장 실패", e);
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    private void saveToFail(MemberSignUpKafkaEvent event ) throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(event);
        FailMessageModel failMessageModel = FailMessageModel
                .builder()
                .topic("member-signup-events")
                .messageType("MEMBER_SIGNUP")
                .payload(payload)
                .retryCount(0)
                .resolved(false)
                .eventId(event.getEventId())
                .exceptionMessage("자동 DLQ 저장")
                .createdAt(LocalDateTime.now())
                .nextRetryTime(LocalDateTime.now().plusSeconds(5)) // 저장되는 즉시 스케줄러가 낚아챌 수 있도록 '현재+5초' 예약
                .build();
        failedMessageService.createFailMessage(failMessageModel);
        log.warn(" DLQ 메시지 저장 완료: {}", payload);
    }
}
