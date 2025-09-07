package com.example.events.process;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional
@AllArgsConstructor
public class ProcessedEventService {

    private final ProcessedEventRepository processedEventRepository;


    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String eventId) {
        return processedEventRepository.existsByEventId(eventId);
    }

    public void saveProcessedEvent(String eventId) {
        try {
            ProcessedEventEntity entity = ProcessedEventEntity.builder()
                    .eventId(eventId)
                    .processedAt(LocalDateTime.now())
                    .build();
            processedEventRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            // 이미 처리된 이벤트라면 무시
            log.warn("이벤트 중복 저장 시도 감지됨: {}", eventId);
        }
    }
}
