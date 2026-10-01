package com.example.events.process;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional
@AllArgsConstructor
public class ProcessedEventService {

    private final ProcessedEventRepository processedEventRepository;


    // consumer: 컨슈머 그룹 이름. 같은 이벤트라도 컨슈머마다 한 번씩은 처리해야 한다.
    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String consumer, String eventId) {
        return processedEventRepository.existsByConsumerAndEventId(consumer, eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveProcessedEvent(String consumer, String eventId) {
            ProcessedEventEntity entity = ProcessedEventEntity.builder()
                    .consumer(consumer)
                    .eventId(eventId)
                    .processedAt(LocalDateTime.now())
                    .build();
            processedEventRepository.save(entity);
    }
}
