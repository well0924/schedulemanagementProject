package com.example.notification.service;

import com.example.notification.model.FailMessageModel;
import com.example.outbound.notification.FailMessageOutConnector;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional
@AllArgsConstructor
public class FailedMessageService {

    private final FailMessageOutConnector failMessageOutConnector;

    @Transactional(readOnly = true)
    public List<FailMessageModel>findByResolvedFalse(){
        return failMessageOutConnector.findByResolvedFalse();
    }

    public FailMessageModel createFailMessage(FailMessageModel failMessageModel){
        return failMessageOutConnector.createFailMessage(failMessageModel);
    }

    @Transactional(readOnly = true)
    public boolean isAlreadyRecorded(String eventId) {
        return failMessageOutConnector.isAlreadyRecorded(eventId);
    }

    public FailMessageModel updateFailMessage(FailMessageModel model) {
        return failMessageOutConnector.updateFailMessage(model);
    }

    public void cleanupOldResolvedMessages() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7); // 예: 7일 이전
        int deleted = failMessageOutConnector.deleteByResolvedIsTrueAndResolvedAtBefore(threshold);
        log.info("🧹 삭제된 실패 메시지 개수 = {}", deleted);
    }

    @Transactional(readOnly = true)
    public List<FailMessageModel> findReadyToRetry() {
        return failMessageOutConnector.findReadyToRetry();
    }

    public void markAsDeadByEventId(String eventId) {
        failMessageOutConnector.findByEventId(eventId) // Optional 반환
                .ifPresent(model -> {
                    model.markAsDead();
                    failMessageOutConnector.updateFailMessage(model);
                });
    }
}
