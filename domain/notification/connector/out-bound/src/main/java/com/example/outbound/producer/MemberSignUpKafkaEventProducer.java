package com.example.outbound.producer;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("!test")
@Component
@AllArgsConstructor
public class MemberSignUpKafkaEventProducer {

    @Qualifier("memberKafkaTemplate")
    private final KafkaTemplate<String, MemberSignUpKafkaEvent> memberKafkaTemplate;
    private static final String TOPIC = "member-signup-events";

    public void send(MemberSignUpKafkaEvent event) {
        log.info("회원가입 Kafka 이벤트 발행: topic={}, event={}", TOPIC, event);
        memberKafkaTemplate.send(TOPIC, event);
    }
}
