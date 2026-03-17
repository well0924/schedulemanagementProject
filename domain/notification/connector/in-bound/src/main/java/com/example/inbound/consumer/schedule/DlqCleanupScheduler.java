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

    //- lockAtMostFor: 서버 장애로 락이 안 풀려도 10분 뒤엔 자동 해제 (Deadlock 방지).
    //- lockAtLeastFor: 최소 1분은 락을 유지하여, 서버 간 미세한 시간 차로 인한 중복 실행 방지.
    @Scheduled(cron = "0 0 3 * * ?") // 매일 새벽 3시
    @SchedulerLock(name = "dlqCleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void cleanupOldResolvedMessages() {
        log.info("DLQ 정리 스케줄러 실행 시작");
        try{
            failedMessageService.cleanupOldResolvedMessages();
            log.info("DLQ 정리 작업이 성공적으로 완료되었습니다.");
        } catch (Exception e) {
            log.error("DLQ 정리 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}
