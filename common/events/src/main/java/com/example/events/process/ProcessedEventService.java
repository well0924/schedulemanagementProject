package com.example.events.process;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    // 컨슈머의 업무 저장과 같은 트랜잭션에 참여해야 한다.
    // 따로 커밋되면 업무 저장이 실패해도 "처리됨"만 남아, 재시도가 건너뛰어 메시지가 사라진다.
    public void saveProcessedEvent(String consumer, String eventId) {
            ProcessedEventEntity entity = ProcessedEventEntity.builder()
                    .consumer(consumer)
                    .eventId(eventId)
                    .processedAt(LocalDateTime.now())
                    .build();
            processedEventRepository.save(entity);
    }
}
