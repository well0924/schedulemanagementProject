package com.example.events.outbox;

import com.example.events.kafka.BaseKafkaEvent;
import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@AllArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository repository;

    @Qualifier("objectKafkaTemplate")
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final ObjectMapper objectMapper;

    /**
     * 3초마다 Outbox의 미전송 이벤트를 조회하여 발행한다.
     * [ShedLock 적용 이유]
     * 1. 단일 실행 보장
     *    배포 또는 재시작 과정에서 인스턴스가 일시적으로 중첩될 가능성이 있으므로 스케줄 작업의 중복 실행을 방지한다.
     * 2. 데이터 정합성 보호
     *    본 작업은 DB 상태 변경 및 외부 이벤트 발행을 수행하므로 중복 실행 시 이벤트 중복 발행이 발생할 수 있다.
     * 3. 확장 대응
     *    향후 다중 인스턴스 환경으로 전환되더라도 추가 코드 수정 없이 동시성 제어가 가능하도록 분산 락을 적용한다.
     */
    @Timed(value = "outbox.publish.duration", description = "Outbox Kafka 발행 처리 시간")
    @Counted(value = "outbox.publish.count", description = "Outbox Kafka 발행 실행 횟수")
    @Scheduled(fixedDelay = 3000) //3초마다 실행
    @SchedulerLock(name = "OutboxPublisherLock", lockAtMostFor = "PT10M", lockAtLeastFor = "PT2S")
    public void publishOutboxEvents() {
        // 전송되지 않은 이벤트를 생성순으로 100건씩 가져와 순차 발행
        List<OutboxEventEntity> events = repository.findTop100BySentFalseOrderByCreatedAtAsc();

        for (OutboxEventEntity event : events) {
            try {
                publishSingleEvent(event);
            } catch (Exception e) {
                event.increaseRetryCount();
                log.error("Kafka 발행 실패 - id={}, error={}", event.getId(), e.getMessage());
                // 실패하면 그대로 두면 됨 → 재시도됨
            }
        }
        updateEvents(events); // 전송 상태 반영
    }

    // Kafka 메시지에 Outbox PK를 eventId로 주입합니다.
    // 컨슈머는 이 ID를 사용하여 이미 처리한 메시지인지 확인(멱등성 체크)함으로써 중복 로직 실행을 방지합니다.
    private void publishSingleEvent(OutboxEventEntity event) throws IOException {
        String topic = event.resolveTopic();
        Object payload = objectMapper.readValue(event.getPayload(), resolveEventClass(event));

        // eventId 주입 (Outbox PK → Kafka eventId)
        // Outbox PK를 이벤트 모델의 eventId에 매핑하여 전송 (At-least-once 전략의 핵심)
        if (payload instanceof BaseKafkaEvent baseEvent) {
            if(baseEvent.getEventId() == null || baseEvent.getEventId().isBlank()) {
                baseEvent.setEventId(event.getId());
                log.info("eventId setting:"+baseEvent.getEventId());
            }
        }

        kafkaTemplate.send(topic, payload)
                .whenComplete((result, ex) -> {
            if (ex == null) {
                // sent를 true로 변경
                event.markSent();
                log.info("Kafka 발행 성공 - topic={}, partition={}, offset={}, eventId={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        event.getId());
            } else {
                // 재시도 횟수 증가
                event.increaseRetryCount();
                log.error("Kafka 발행 실패 - topic={}, eventId={}, error={}",
                        topic, event.getId(), ex.getMessage(), ex);
            }
        });
    }

    // Outbox 이벤트 상태를 DB에 반영한다.
    // 5회 이상 실패를 하면 sent=false → DLQ 토픽으로 이동
    // DLQ 전송 성공 시 Outbox에서 삭제하여 중복 발행 방지
    @Transactional
    public void updateEvents(List<OutboxEventEntity> events) {
        for (OutboxEventEntity event : events) {
            if (event.getRetryCount() > 5 && !event.getSent()) {
                try {
                    // DLQ 토픽 이름 결정
                    String dlqTopic = resolveDlqTopic(event);
                    // 원래 payload 그대로 DLQ 토픽으로 전송 (비동기 callback 처리)
                    kafkaTemplate.send(dlqTopic, event.getId().toString(), event.getPayload())
                            .whenComplete((result, ex) -> {
                                if (ex == null) {
                                    log.warn("DLQ 전송 성공 - eventId={}, type={}, dlqTopic={}",
                                            event.getId(), event.getEventType(), dlqTopic);
                                } else {
                                    log.error("DLQ 전송 실패 - eventId={}, dlqTopic={}, error={}",
                                            event.getId(), dlqTopic, ex.getMessage(), ex);
                                }
                            });
                    // Outbox에서는 삭제해서 중복 발행 방지
                    repository.delete(event);
                } catch (Exception e) {
                    log.error("DLQ 처리 중 예외 발생 - eventId={}, error={}", event.getId(), e.getMessage(), e);
                }
            }
        }
        repository.saveAll(events);
    }

    private String resolveDlqTopic(OutboxEventEntity event) {
        return switch (event.getAggregateType()) {
            case "MEMBER" -> "member-signup-events.DLQ";
            case "SCHEDULE" -> "notification-events.DLQ";
            default -> throw new IllegalArgumentException("지원하지 않는 AggregateType: " + event.getAggregateType());
        };
    }

    private Class<?> resolveEventClass(OutboxEventEntity event) {
        return switch (event.getAggregateType()) {
            case "MEMBER" -> MemberSignUpKafkaEvent.class;
            case "SCHEDULE" -> NotificationEvents.class;
            default -> throw new IllegalArgumentException("지원하지 않는 AggregateType: " + event.getAggregateType());
        };
    }
}
