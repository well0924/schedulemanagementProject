package com.example.inbound.attach;

import com.example.model.attach.AttachModel;
import com.example.model.attach.FailedThumbnailModel;
import com.example.service.attach.AttachService;
import com.example.service.attach.ThumbnailService;
import com.example.service.failthumbnail.FailedThumbnailService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@AllArgsConstructor
public class FailedThumbnailScheduler {

    private final FailedThumbnailService failedThumbnailService;
    private final ThumbnailService thumbnailService;
    private final AttachService attachService;


    @Scheduled(cron = "0 */5 * * * *")
    public void retryFailedThumbnails() {
        MDC.put("job", "thumbnail-retry");
        MDC.put("requestId", java.util.UUID.randomUUID().toString());

        try {
            log.info("🔄 썸네일 재처리 스케줄러 시작");
            List<FailedThumbnailModel> failedList = failedThumbnailService.findRetryTargets(3, 20);

            if (failedList.isEmpty()) {
                log.debug("실패 항목 없음. 재처리 스킵");
                return;
            }

            for (FailedThumbnailModel target : failedList) {
                try {
                    AttachModel attach = attachService.findByStoredFileName(target.getStoredFileName());
                    thumbnailService.createAndUploadThumbnail(attach);
                    if (attach.getThumbnailFilePath() != null && !attach.getThumbnailFilePath().isBlank()) {
                        failedThumbnailService.markResolved(target.getId());
                        log.info("재처리 성공: {}", target.getStoredFileName());
                    } else {
                        failedThumbnailService.increaseRetryCount(target.getId(), "Thumbnail URL missing after retry");
                        log.warn("재처리 실패(썸네일 URL 미생성): {}", target.getStoredFileName());
                    }
                } catch (Exception e) {
                    failedThumbnailService.increaseRetryCount(target.getId(), e.getMessage());
                    log.warn("재처리 실패: {}", target.getStoredFileName(), e);
                }  finally {
                    MDC.remove("storedFile");
                }
            }
            log.info("썸네일 재처리 스케줄러 종료 (총 {}건)", failedList.size());
        } finally {
            MDC.remove("job");
            MDC.remove("requestId");
        }
    }
}
