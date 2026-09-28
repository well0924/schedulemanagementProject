package com.example.inbound.consumer.member;

import com.example.inbound.consumer.slack.SlackNotifier;
import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.logging.MDC.KafkaMDCUtil;
import com.example.notification.model.FailMessageModel;
import com.example.notification.service.FailedMessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class MemberSignUpRetryTopicConsumer {

    @Qualifier("memberKafkaTemplate")
    private final KafkaTemplate<String, MemberSignUpKafkaEvent> kafkaTemplate;

    private final MeterRegistry meterRegistry;

    private final SlackNotifier slackNotifier;

    private final FailedMessageService failedMessageService;

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "member-signup.retry.5s", groupId = "member-retry-5s")
    public void retry5s(String message, Acknowledgment ack) {
        try {
            MemberSignUpKafkaEvent event = objectMapper.readValue(message, MemberSignUpKafkaEvent.class);
            KafkaMDCUtil.initMDC(event);
            meterRegistry.counter("kafka.retry.signup.success", "delay", "5s").increment();
            kafkaTemplate.send("member-signup-events", event);
            log.info(" 5초 후 재전송 완료: {}", event.getEmail());
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            // 역직렬화 불가 메시지는 재시도해도 실패하므로 커밋하고 넘어간다
            ack.acknowledge();
            meterRegistry.counter("kafka.retry.signup.failure", "delay", "5s").increment();
            log.error("회원가입 재시도 - 역직렬화 실패: {}", message, e);
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "member-signup.retry.10s", groupId = "member-retry-10s")
    public void retry10s(String message, Acknowledgment ack) {
        try {
            MemberSignUpKafkaEvent event = objectMapper.readValue(message, MemberSignUpKafkaEvent.class);
            KafkaMDCUtil.initMDC(event);
            meterRegistry.counter("kafka.retry.signup.success", "delay", "10s").increment();
            kafkaTemplate.send("member-signup-events", event);
            log.info(" 10초 후 재전송 완료: {}", event.getEmail());
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            // 역직렬화 불가 메시지는 재시도해도 실패하므로 커밋하고 넘어간다
            ack.acknowledge();
            meterRegistry.counter("kafka.retry.signup.failure", "delay", "10s").increment();
            log.error("회원가입 재시도 - 역직렬화 실패: {}", message, e);
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "member-signup.retry.30s", groupId = "member-retry-30s")
    public void retry30s(String message, Acknowledgment ack) {
        try {
            MemberSignUpKafkaEvent event = objectMapper.readValue(message, MemberSignUpKafkaEvent.class);
            KafkaMDCUtil.initMDC(event);
            meterRegistry.counter("kafka.retry.signup.success", "delay", "30s").increment();
            kafkaTemplate.send("member-signup-events", event);
            log.info(" 30초 후 재전송 완료: {}", event.getEmail());
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            // 역직렬화 불가 메시지는 재시도해도 실패하므로 커밋하고 넘어간다
            ack.acknowledge();
            meterRegistry.counter("kafka.retry.signup.failure", "delay", "30s").increment();
            log.error("회원가입 재시도 - 역직렬화 실패: {}", message, e);
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "member-signup.retry.60s", groupId = "member-retry-60s")
    public void retry60s(String message, Acknowledgment ack) {
        try {
            MemberSignUpKafkaEvent event = objectMapper.readValue(message, MemberSignUpKafkaEvent.class);
            KafkaMDCUtil.initMDC(event);
            meterRegistry.counter("kafka.retry.signup.success", "delay", "60s").increment();
            kafkaTemplate.send("member-signup-events", event);
            log.info(" 60초 후 재전송 완료: {}", event.getEmail());
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            // 역직렬화 불가 메시지는 재시도해도 실패하므로 커밋하고 넘어간다
            ack.acknowledge();
            meterRegistry.counter("kafka.retry.signup.failure", "delay", "60s").increment();
            log.error("회원가입 재시도 - 역직렬화 실패: {}", message, e);
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "member-signup.retry.final", groupId = "member-retry-final")
    public void retryFinal(String message, Acknowledgment ack) {
        try {
            MemberSignUpKafkaEvent event = objectMapper.readValue(message, MemberSignUpKafkaEvent.class);
            KafkaMDCUtil.initMDC(event);
            // 죽여줘야 스케줄러가 다시 안 긁음.
            failedMessageService.markAsDeadByEventId(event.getEventId());
            String test = String.format("- EventId: %s%n- Topic: %s%n- Exception: %s",
                    event.getEventId(),event.getNotificationType(),event.getMessage());
            // 최종 실패 → Slack 알림처리하기.(추후 구현)
            meterRegistry.counter("kafka.retry.signup.failure.final").increment();
            slackNotifier.send("Dlq_Notification",test);
            log.warn(" 회원가입 최종 재시도 실패: {}", event.getEmail());
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            // 역직렬화 불가 메시지는 재시도해도 실패하므로 커밋하고 넘어간다
            ack.acknowledge();
            log.error("회원가입 최종 재시도 - 역직렬화 실패: {}", message, e);
        } catch (Exception e) {
            log.error("최종 실패 처리 중 에러 발생: {}", e.getMessage());
        } finally {
            KafkaMDCUtil.clear();
        }
    }
}
