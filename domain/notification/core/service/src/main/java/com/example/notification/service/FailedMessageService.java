package com.example.notification.service;

import com.example.notification.model.FailMessageModel;
import com.example.outbound.notification.FailMessageOutConnector;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    // DLQ로 들어온 실패를 기록한다. 같은 이벤트·유형의 행이 이미 있으면 새로 만들지 않는다.
    // - 기록 없음: 새로 저장 (첫 실패)
    // - 재발행 후 다시 실패해 돌아옴(resolved=true): 같은 행을 다시 열어 retryCount를 올리고 다음 단계로 예약
    // - 아직 재처리 대기 중(resolved=false)이거나 포기한 행(dead=true): DLQ 중복 전달이므로 무시
    public void recordDlqFailure(FailMessageModel failure) {
        Optional<FailMessageModel> existing = failMessageOutConnector
                .findByEventIdAndMessageType(failure.getEventId(), failure.getMessageType());
        if (existing.isEmpty()) {
            failMessageOutConnector.createFailMessage(failure);
            return;
        }
        FailMessageModel row = existing.get();
        if (row.isDead() || !row.isResolved()) {
            log.info("DLQ 중복 전달 무시 - eventId={}, dead={}, resolved={}", row.getEventId(), row.isDead(), row.isResolved());
            return;
        }
        row.reopen(failure.getExceptionMessage());
        failMessageOutConnector.updateFailMessage(row);
        log.warn("재발행 후 다시 실패 - eventId={}, retryCount={}, nextRetryTime={}",
                row.getEventId(), row.getRetryCount(), row.getNextRetryTime());
    }

    public void markAsDeadByEventId(String eventId, String messageType) {
        failMessageOutConnector.findByEventIdAndMessageType(eventId, messageType)
                .ifPresent(model -> {
                    model.markAsDead();
                    failMessageOutConnector.updateFailMessage(model);
                });
    }
}
