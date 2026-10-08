package com.example.inbound.consumer.member;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.process.ProcessedEventService;
import com.example.interfaces.notification.kafka.KafkaEventConsumer;
import com.example.logging.MDC.KafkaMDCUtil;
import com.example.notification.NotificationType;
import com.example.notification.email.EmailService;
import com.example.notification.model.NotificationModel;
import com.example.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberSignUpKafkaEventConsumer implements KafkaEventConsumer<MemberSignUpKafkaEvent> {

    // 중복 처리 판단 단위. 리스너 groupId와 같은 값이어야 한다.
    private static final String CONSUMER = "member-group";

    private final EmailService emailService;

    private final NotificationService notificationService;

    private final ProcessedEventService processedEventService;

    private final SimpMessagingTemplate simpMessagingTemplate;

    private final ObjectMapper objectMapper;

    private final TransactionTemplate transactionTemplate;

    /**
     * 알림 내역과 처리 기록은 한 트랜잭션으로 저장하고, 메일·실시간 알림과 오프셋 커밋은
     * 그 트랜잭션이 끝나 커넥션을 반납한 뒤에 한다. 저장 중 예외는 전파해 재시도 → DLQ로 보낸다.
     * 메일을 저장 뒤로 둔 이유: 저장 실패로 재시도될 때마다 환영 메일이 다시 나가지 않게 하려고.
     * 메일은 재시도(최대 3회, 2초 간격)와 실패 기록 저장이 있어 트랜잭션 밖에서 보내야 한다.
     */
    @Timed(value = "kafka.consumer.signup.duration", description = "회원가입 Kafka 메시지 처리 시간")
    @Counted(value = "kafka.consumer.signup.count", description = "회원가입 Kafka 메시지 수신 횟수")
    @KafkaListener(
            topics = "member-signup-events",
            groupId = CONSUMER,
            containerFactory = "memberKafkaListenerFactory"
    )
    @Override
    public void handle(MemberSignUpKafkaEvent event, Acknowledgment ack) {
        try {
            KafkaMDCUtil.initMDC(event);
            // 1. 멱등성 확인
            if (processedEventService.isAlreadyProcessed(CONSUMER, event.getEventId())) {
                log.info("⚠️ 이미 처리된 이벤트 무시: {}", event.getEventId());
                ack.acknowledge();
                return;
            }
            // 2. 알림 내역 + 처리 기록 저장 (같은 트랜잭션)
            transactionTemplate.executeWithoutResult(status -> {
                saveNotificationToDatabase(event);
                processedEventService.saveProcessedEvent(CONSUMER, event.getEventId());
            });

            // 3. 커밋·커넥션 반납 후: 메일·실시간 알림 → 오프셋 커밋
            sendWelcomeEmail(event);
            sendRealtimeNotification(event);
            ack.acknowledge();
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    private void saveNotificationToDatabase(MemberSignUpKafkaEvent event) {
        NotificationModel notification = NotificationModel
                .builder()
                .userId(event.getReceiverId()) // 알림을 받을 사용자 ID
                .message("🎉 환영합니다, " + event.getUsername() + "님! 회원가입이 완료되었습니다.") // 알림 메시지
                .notificationType(NotificationType.SIGN_UP_WELCOME) // 알림 타입
                .createdTime(LocalDateTime.now()) // 알림 생성 시간
                .isRead(false) // 기본값은 false
                .build();

        notificationService.createNotification(notification);
        log.info("회원가입 알림 저장 성공: {}", event.getReceiverId());
    }

    // 메일 실패는 재시도하지 않는다 (알림 내역은 이미 저장됨)
    private void sendWelcomeEmail(MemberSignUpKafkaEvent event) {
        try {
            emailService.sendHtmlEmail(event.getEmail(), "🎉 회원가입을 환영합니다!", buildWelcomeEmailContent(event.getUsername()));
            log.info("회원가입 환영 메일 발송 성공: {}", event.getEmail());
        } catch (Exception emailEx) {
            log.error("이메일 발송 실패 (재시도 안 함): {}", emailEx.getMessage(), emailEx);
        }
    }

    private void sendRealtimeNotification(MemberSignUpKafkaEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            simpMessagingTemplate.convertAndSend("/topic/memberSignUp/" + event.getReceiverId(), message);
        } catch (Exception e) {
            log.error("회원가입 실시간 알림 전달 실패 (내역은 저장됨): eventId={}", event.getEventId(), e);
        }
    }

    private String buildWelcomeEmailContent(String username) {
        return String.format("""
            <html>
                <body style="font-family: Arial, sans-serif; padding: 20px;">
                    <h1>환영합니다, %s님! 🎉</h1>
                    <p>저희 서비스를 이용해주셔서 감사합니다.</p>
                    <p>이제 일정을 등록하고 관리하면서 하루를 더욱 알차게 보내보세요!</p>
                    <br/>
                    <a href="localhost:8082/login" 
                       style="display:inline-block; padding:10px 20px; background-color:#4CAF50; 
                              color:white; text-decoration:none; border-radius:5px;">
                        지금 시작하기
                    </a>
                    <br/><br/>
                    <p>문의사항이 있으면 언제든지 연락주세요.</p>
                    <p style="font-size:12px; color:gray;">© 2025 Your Company Name. All rights reserved.</p>
                </body>
            </html>
            """, username);
    }
}
