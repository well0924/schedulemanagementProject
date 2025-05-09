package com.example.notification.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Retryable(
            value = { MessagingException.class, RuntimeException.class }, // 이 예외 터지면 리트라이
            maxAttempts = 3, // 최대 3번 시도
            backoff = @Backoff(delay = 2000) // 2초 쉬고 다시 시도
    )
    public void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        log.info("이메일 발송 시도: 수신자={}, 제목={}", to, subject);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true); // true = HTML 메일

        mailSender.send(message);

        log.info("이메일 발송 성공: 수신자={}", to);
    }

    @Recover
    public void recover(MessagingException e, String to, String subject, String htmlContent) {
        log.error("이메일 발송 최종 실패: 수신자={}, 제목={}", to, subject, e);
        // 여기서 실패한 이메일을 DB 저장하거나 슬랙 알림 보낼 수도 있음
    }

}
