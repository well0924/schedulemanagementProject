package com.example.kafka.dlq;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("test")
@Component
public class TestFailingConsumer {

    // 회원 가입 실패시 사용되는 리스너
    @KafkaListener(topics = "member-signup-events", containerFactory = "testMemberKafkaListenerFactory")
    public void consume(MemberSignUpKafkaEvent event) {
        if ("강제실패 알림".equals(event.getMessage())) {
            log.info("[TestFailingConsumer] 테스트 메시지 감지됨. 강제 실패 유도!");
            throw new RuntimeException("강제 실패: DLQ 테스트용");
        }
    }

    //일정 CRUD 실패시에 사용되는 리스너
    @KafkaListener(topics = "notification-events", containerFactory = "testNotificationKafkaListenerFactory")
    public void consume(NotificationEvents events) {
        if ("강제실패 알림".equals(events.getMessage())) {
            log.info("[TestFailingConsumer] 테스트 메시지 감지됨. 강제 실패 유도!");
            throw new RuntimeException("강제 실패: DLQ 테스트용");
        }
    }
}
