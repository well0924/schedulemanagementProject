package com.example.outbound.notification;

import com.example.notification.mapper.NotificationEntityMapper;
import com.example.notification.mapper.NotificationMapper;
import com.example.notification.model.FailMessageModel;
import com.example.rdbrepository.FailedMessage;
import com.example.rdbrepository.FailedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailMessageOutConnector {

    private final FailedMessageRepository failedMessageRepository;

    private final NotificationMapper notificationMapper;

    private final NotificationEntityMapper notificationEntityMapper;

    public List<FailMessageModel> findByResolvedFalse (){
        return failedMessageRepository.findByResolvedFalse()
                .stream()
                .map(notificationMapper::toModel)
                .collect(Collectors.toList());
    }

    // 실패 내역 저장 (DLQ 컨슈머가 메시지를 수신했을 때 최초로 호출)
    public FailMessageModel createFailMessage(FailMessageModel failMessageModel) {
        FailedMessage failedMessage = notificationEntityMapper.toEntity(failMessageModel);
        return notificationMapper.toModel(failedMessageRepository.save(failedMessage));
    }

    // 실패 내역 상태 수정 (재시도 횟수 증가, 성공 마킹, 전사 처리 등).
    public FailMessageModel updateFailMessage(FailMessageModel model) {
        if (!failedMessageRepository.existsById(model.getId())) {
            throw new IllegalArgumentException("업데이트하려는 FailMessage가 존재하지 않습니다. id=" + model.getId());
        }
        FailedMessage toUpdate = notificationEntityMapper.toEntity(model);
        return notificationMapper.toModel(failedMessageRepository.save(toUpdate));
    }

    // 실패 내역 조회(최종 실패(retry.final) 단계에서 상태를 DEAD로 변경하기 위해 모델을 불러올 때 사용.)
    public Optional<FailMessageModel> findByEventId(String eventId) {
        return failedMessageRepository.findByEventId(eventId)
                .map(notificationMapper::toModel);
    }

    // DLQ에 중복 저장되는 것을 방지하기 위해 boolean값만 빠르게 리턴함
    public boolean isAlreadyRecorded(String eventId) {
        return failedMessageRepository.existsByEventId(eventId);
    }

    // 성공한 지 오래된(threshold 이전) 데이터를 지움.
    public int deleteByResolvedIsTrueAndResolvedAtBefore(LocalDateTime threshold) {
        return failedMessageRepository.deleteByResolvedIsTrueAndResolvedAtBefore(threshold);
    }

    // 재시도 준비가 된 메시지 선별 1. 미해결(resolved=false) 2. 생존(dead=false) 3. 예약시간 도래(nextRetryTime <= now)
    // 세 가지 조건을 모두 만족하는 데이터만 리턴함.
    public List<FailMessageModel> findReadyToRetry() {
        // 현재 시간(LocalDateTime.now())보다 이전에 실행되어야 할 데이터만 가져옴
        return failedMessageRepository.findByResolvedFalseAndDeadFalseAndNextRetryTimeBefore(LocalDateTime.now())
                .stream()
                .map(notificationMapper::toModel)
                .collect(Collectors.toList());
    }
}
