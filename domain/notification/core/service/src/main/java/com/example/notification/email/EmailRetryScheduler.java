package com.example.notification.email;

import com.example.notification.model.FailEmailModel;
import com.example.outbound.email.FailEmailOutConnector;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@AllArgsConstructor
public class EmailRetryScheduler {

    private final FailEmailOutConnector failEmailOutConnector;

    private final EmailService emailService;

    @Scheduled(fixedDelay = 10_000) // 10초마다 재시도
    public void retryFailedEmails() {
        List<FailEmailModel> fails = failEmailOutConnector.findUnresolved();
        for (FailEmailModel fail : fails) {
            try {
                //이메일 재전송
                emailService.sendHtmlEmail(fail.getToEmail(), fail.getSubject(), fail.getContent());
                fail.markResolved();
            } catch (Exception e) {
                log.warn("재시도 실패 - id={}, reason={}", fail.getId(), e.getMessage());
            }
        }
        failEmailOutConnector.saveAll(fails); // 일괄 저장
    }
}
