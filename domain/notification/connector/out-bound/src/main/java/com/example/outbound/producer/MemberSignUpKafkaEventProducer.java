package com.example.outbound.producer;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class MemberSignUpKafkaEventProducer {

    private final KafkaTemplate<String, MemberSignUpKafkaEvent> memberKafkaTemplate;
    private static final String TOPIC = "member-signup-events";

    public void send(MemberSignUpKafkaEvent event) {
        log.info("회원가입 Kafka 이벤트 발행: topic={}, event={}", TOPIC, event);
        memberKafkaTemplate.send(TOPIC, event);
    }
}
