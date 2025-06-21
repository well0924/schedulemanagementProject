package com.example.inbound.consumer.member;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.logging.MDC.KafkaMDCUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class MemberSignUpRetryTopicConsumer {

    private final KafkaTemplate<String, MemberSignUpKafkaEvent> kafkaTemplate;

    @KafkaListener(topics = "member-signup.retry.5s", groupId = "member-retry-5s")
    public void retry5s(MemberSignUpKafkaEvent event) {
        try{
            KafkaMDCUtil.initMDC(event);
            kafkaTemplate.send("member-signup-events", event);
            log.info(" 5초 후 재전송 완료: {}", event.getEmail());
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "member-signup.retry.10s", groupId = "member-retry-10s")
    public void retry10s(MemberSignUpKafkaEvent event) {
        try {
            KafkaMDCUtil.initMDC(event);
            kafkaTemplate.send("member-signup-events", event);
            log.info(" 10초 후 재전송 완료: {}", event.getEmail());
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "member-signup.retry.30s", groupId = "member-retry-30s")
    public void retry30s(MemberSignUpKafkaEvent event) {
        try {
            KafkaMDCUtil.initMDC(event);
            kafkaTemplate.send("member-signup-events", event);
            log.info(" 30초 후 재전송 완료: {}", event.getEmail());
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "member-signup.retry.60s", groupId = "member-retry-60s")
    public void retry60s(MemberSignUpKafkaEvent event) {
        try {
            KafkaMDCUtil.initMDC(event);
            kafkaTemplate.send("member-signup-events", event);
            log.info(" 60초 후 재전송 완료: {}", event.getEmail());
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    @KafkaListener(topics = "member-signup.retry.final", groupId = "member-retry-final")
    public void retryFinal(MemberSignUpKafkaEvent event) {
        try {
            KafkaMDCUtil.initMDC(event);
            // 최종 실패 → Slack 알림처리하기.(추후 구현)
            log.warn(" 회원가입 최종 재시도 실패: {}", event.getEmail());
        } finally {
            KafkaMDCUtil.clear();
        }
    }
}
