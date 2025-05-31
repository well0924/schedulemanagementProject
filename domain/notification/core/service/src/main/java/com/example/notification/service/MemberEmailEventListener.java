package com.example.notification.service;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.spring.MemberSignUpEvent;
import com.example.notification.NotificationType;
import com.example.interfaces.notification.event.NotificationEventInterfaces;
import com.example.outbound.producer.MemberSignUpKafkaEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberEmailEventListener implements NotificationEventInterfaces<MemberSignUpEvent> {

    private final MemberSignUpKafkaEventProducer memberSignUpKafkaEventProducer;

    @Async
    @EventListener
    public void handleSchedule(MemberSignUpEvent event) {
        // 내부에서 추상화된 메서드 호출
        handle(event);
    }

    @Override
    public void handle(MemberSignUpEvent handle) {
        log.debug("event!::"+ handle.getUsername());
        
        //카프카 발송 이벤트
        MemberSignUpKafkaEvent kafkaEvent = MemberSignUpKafkaEvent
                .builder()
                .receiverId(handle.getMemberId())
                .message("🎉 환영합니다, " + handle.getUsername() + "님! 회원가입이 완료되었습니다.") // 알림 메시지
                .notificationType(String.valueOf(NotificationType.SIGN_UP_WELCOME)) // 알림 타입
                .createdTime(LocalDateTime.now()) // 알림 생성 시간
                .build();
        //프로듀서로 전송.
        memberSignUpKafkaEventProducer.send(kafkaEvent);
        log.info("📤 Kafka 전송 완료 → member-signup-events");
    }
}
