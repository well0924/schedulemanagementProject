package com.example.notification.service;

import com.example.events.MemberSignUpEvent;
import com.example.notification.NotificationType;
import com.example.notification.apimodel.NotificationEvents;
import com.example.notification.email.EmailService;
import com.example.notification.interfaces.NotificationEventInterfaces;
import com.example.notification.model.NotificationModel;
import com.example.notification.outconnector.NotificationOutConnector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberEmailEventListener implements NotificationEventInterfaces<MemberSignUpEvent> {

    private final NotificationEventProducer notificationEventProducer;

    private final EmailService emailService;

    private final NotificationService notificationService;


    @Async
    @EventListener
    @Override
    public void handle(MemberSignUpEvent handle) {
        log.debug("event!::"+handle.getUsername());
        //이메일 전송
        //sendWelcomeEmail(handle);
        log.debug("notice::");
        //디비 저장
        saveNotificationToDatabase(handle);
        //알림
        sendRealTimeNotification(handle);
    }

    private void sendWelcomeEmail(MemberSignUpEvent event) {
        try {
            emailService.sendHtmlEmail(
                    event.getEmail(),
                    "🎉 회원가입을 환영합니다!",
                    buildWelcomeEmailContent(event.getUsername())
            );
            log.info("회원가입 환영 메일 발송 성공: {}", event.getEmail());
        } catch (Exception e) {
            log.error("회원가입 환영 메일 발송 실패: {}", e.getMessage());
        }
    }

    private void sendRealTimeNotification(MemberSignUpEvent event) {
        try {


            NotificationEvents notificationEvent = NotificationEvents.builder()
                    .receiverId(event.getMemberId())           // 회원가입한 유저 ID
                    .message("🎉 환영합니다, " + event.getUsername() + "님! 회원가입이 완료되었습니다.")
                    .notificationType("SIGN_UP_WELCOME")       // 알림 타입 문자열
                    .createdTime(LocalDateTime.now())          // 생성 시간
                    .build();

            notificationEventProducer.sendNotification(notificationEvent);
            log.info("회원가입 실시간 알림 전송 성공: {}", event.getMemberId());

        } catch (Exception e) {
            log.error("회원가입 실시간 알림 전송 실패: {}", e.getMessage());
        }
    }

    private void saveNotificationToDatabase(MemberSignUpEvent event) {
        try {
            NotificationModel notification = NotificationModel
                    .builder()
                    .userId(event.getMemberId()) // 알림을 받을 사용자 ID
                    .message("🎉 환영합니다, " + event.getUsername() + "님! 회원가입이 완료되었습니다.") // 알림 메시지
                    .notificationType(NotificationType.SIGN_UP_WELCOME) // 알림 타입
                    .createdTime(LocalDateTime.now()) // 알림 생성 시간
                    .isRead(false) // 기본값은 false
                    .build();
            notificationService.createNotification(notification);
            log.info("회원가입 알림 저장 성공: {}", event.getMemberId());
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
