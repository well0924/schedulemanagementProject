package com.example.inbound.consumer.member;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.interfaces.notification.kafka.KafkaDlqConsumer;
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
public class DlqMemberSignRetryConsumer implements KafkaDlqConsumer {

    private final FailedMessageService failedMessageService;
    private final ObjectMapper objectMapper;

    // 기본 리스너 팩토리(StringDeserializer)를 쓰므로 문자열로 받아 직접 역직렬화한다.
    // (MemberSignUpKafkaEvent 타입으로 받으면 String → 객체 변환이 불가해 MessageConversionException 발생)
    @Timed(value = "kafka.dlq.signup.save.duration", description = "회원가입 DLQ 저장 처리 시간")
    @KafkaListener(topics = "member-signup-events.DLQ", groupId = "dlq-retry-group")
    @Override
    public void consume(String message, Acknowledgment ack) {
        try {
            MemberSignUpKafkaEvent event = objectMapper.readValue(message, MemberSignUpKafkaEvent.class);
            log.warn(" DLQ 재처리 (member signup): {}", event);
            KafkaMDCUtil.initMDC(event);
            // 멱등성 체크 (재전달 시 중복 저장 방지)
            if (failedMessageService.isAlreadyRecorded(event.getEventId())) {
                ack.acknowledge();
                return;
            }
            saveToFail(event);
            // 실패 내역 저장성공시 카프카에 커밋
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            // 역직렬화 불가 메시지는 재시도해도 실패하므로 커밋하고 넘어간다
            ack.acknowledge();
            log.error(" DLQ 메시지 역직렬화 실패: {}", message, e);
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
