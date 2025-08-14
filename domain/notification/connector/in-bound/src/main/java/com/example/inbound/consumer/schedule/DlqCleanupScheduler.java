package com.example.inbound.consumer.schedule;

import com.example.notification.service.FailedMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqCleanupScheduler {

    private final FailedMessageService failedMessageService;

    @Scheduled(cron = "0 0 3 * * ?") // 매일 새벽 3시
    @SchedulerLock(name = "dlqCleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void cleanupOldResolvedMessages() {
        log.info("DLQ 정리 스케줄러 실행 시작");
        failedMessageService.cleanupOldResolvedMessages();
    }
}
