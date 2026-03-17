package com.example.inbound.consumer.member;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.process.ProcessedEventService;
import com.example.exception.dto.ErrorCode;
import com.example.exception.global.CustomExceptionHandler;
import com.example.interfaces.notification.kafka.KafkaEventConsumer;
import com.example.logging.MDC.KafkaMDCUtil;
import com.example.notification.NotificationType;
import com.example.notification.email.EmailService;
import com.example.notification.model.NotificationModel;
import com.example.notification.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberSignUpKafkaEventConsumer implements KafkaEventConsumer<MemberSignUpKafkaEvent> {

    private final EmailService emailService;

    private final NotificationService notificationService;

    private final ProcessedEventService processedEventService;

    private final SimpMessagingTemplate simpMessagingTemplate;

    private final ObjectMapper objectMapper;

    @Timed(value = "kafka.consumer.signup.duration", description = "회원가입 Kafka 메시지 처리 시간")
    @Counted(value = "kafka.consumer.signup.count", description = "회원가입 Kafka 메시지 수신 횟수")
    @KafkaListener(
            topics = "member-signup-events",
            groupId = "member-group",
            containerFactory = "memberKafkaListenerFactory"
    )
    @Override
    @Transactional //1.비즈니스 로직과 멱등성 저장을 한 원자성으로 묶음
    public void handle(MemberSignUpKafkaEvent event, Acknowledgment ack) { //2.Acknowledgment 추가
        try {
            KafkaMDCUtil.initMDC(event);
            //3. 멱등성 중복 처리 로직
            if (processedEventService.isAlreadyProcessed(event.getEventId())) {
                log.info("⚠️ 이미 처리된 이벤트 무시: {}", event.getEventId());
                throw new CustomExceptionHandler("중복 이벤트 처리됨: " + event.getEventId(), ErrorCode.EVENT_DUPLICATE);
            }
            //4. 이메일 발송 (실패해도 서비스 진행은 계속)
            try {
                emailService.sendHtmlEmail(event.getEmail(), "🎉 회원가입을 환영합니다!", buildWelcomeEmailContent(event.getUsername()));
                log.info("회원가입 환영 메일 발송 성공: {}", event.getEmail());
            } catch (Exception emailEx) {
                log.error("이메일 발송 실패 (계속 진행): {}", emailEx.getMessage(), emailEx);
            }
            //4.알림 내역 저장
            saveNotificationToDatabase(event);
            //5.알림 발송.
            String message = objectMapper.writeValueAsString(event);
            simpMessagingTemplate.convertAndSend("/topic/memberSignUp/" + event.getReceiverId(), message);
            //6.이벤트 저장
            processedEventService.saveProcessedEvent(event.getEventId());
            //7.최종 성공 커밋
            ack.acknowledge();
        } catch (CustomExceptionHandler ex) {
            if(ex.getErrorCode()==ErrorCode.EVENT_DUPLICATE) {
                log.warn("[Kafka Non-Retry Error] code={}, msg={}", ex.getErrorCode(), ex.getMessage());
                // 중복이 된 경우 카프카에게 중복된 메시지라고 알리기
                ack.acknowledge();
            } else {
                log.error("기타 비즈니스 예외: {}", ex.getMessage());
                throw ex;
            }
        } catch(JsonProcessingException e) {
            log.error("Kafka 메시지 직렬화 오류: {}", e.getMessage());
            ack.acknowledge();
            throw new CustomExceptionHandler("이벤트 직렬화 실패: " + event.getEventId(), ErrorCode.EVENT_SERIALIZATION_ERROR);
        } catch (Exception e) {
            log.error("WebSocket 전송 실패 (DLQ 안 보냄)", e);
        } finally {
            KafkaMDCUtil.clear();
        }
    }

    private void saveNotificationToDatabase(MemberSignUpKafkaEvent event) {
        try {

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
        } catch (Exception e) {
            log.error("회원가입 알림 저장 실패: {}", e.getMessage());
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
