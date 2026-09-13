package com.example.outbound.schedule;

import com.example.events.enums.AggregateType;
import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.events.kafka.NotificationEvents;
import com.example.events.outbox.OutboxEventService;
import com.example.events.spring.ScheduleDomainEvent;
import com.example.events.spring.ScheduleEvents;
import com.example.interfaces.notification.notification.NotificationInterfaces;
import com.example.model.schedules.SchedulesModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleEventListener {

    private final NotificationChannelResolver notificationChannelResolver;
    private final OutboxEventService outboxEventService;
    private final NotificationInterfaces notificationInterfaces;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleScheduleDomainEvent(ScheduleDomainEvent event) {
        log.info("[Outbox 적재 시작] 수신된 도메인 이벤트 Action: {}, 대상 건수: {}건",
                event.actionType(), event.schedules().size());

        List<SchedulesModel> targets = event.schedules();
        List<Object> eventDtos = new ArrayList<>();
        List<String> aggregateIds = new ArrayList<>();
        List<String> eventTypes = new ArrayList<>();

        for (SchedulesModel model : targets) {
            // 1. 유저별 알림 채널 동적 결정 (웹알림 우선 혹은 푸시 우선)
            NotificationChannel channel = notificationChannelResolver.resolveChannel(model.getMemberId());

            // 2. 외부 카프카로 전송될 공통 Notification 구조체 래핑
            NotificationEvents kafkaEvent = NotificationEvents.of(ScheduleEvents.builder()
                    .scheduleId(model.getId())
                    .startTime(model.getStartTime())
                    .contents(model.getContents())
                    .userId(model.getMemberId())
                    .notificationChannel(channel)
                    .notificationType(event.actionType())
                    .createdTime(model.getCreatedTime())
                    .build());

            eventDtos.add(kafkaEvent);
            aggregateIds.add(model.getId().toString());
            eventTypes.add(event.actionType().name());
        }

        // 3. Outbox 서비스 호출하여 하나의 쿼리로 벌크 인서트
        if (!eventDtos.isEmpty()) {
            outboxEventService.saveAllEvents(
                    eventDtos,
                    AggregateType.SCHEDULE.name(),
                    aggregateIds,
                    eventTypes
            );
            log.info("[Outbox 적재 완료] 동일 트랜잭션 내 Outbox 데이터 세팅 완료");
        }
    }

    // 리마인더는 outbox처럼 원자성이 필요하지 않은 부가 기능이라 AFTER_COMMIT으로 분리
    // (메인 트랜잭션의 커넥션 점유시간 단축 목적, 2026-09-11).
    // 트레이드오프: 커밋 이후 실패하면 자동 재시도가 없다 - 실패 시 로그로만 추적한다.
    // 기존 direct-call 동작과 동일하게 첫 번째 스케줄에 대해서만 리마인더를 생성한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReminderRegistration(ScheduleDomainEvent event) {
        if (event.actionType() != ScheduleActionType.SCHEDULE_CREATED
                && event.actionType() != ScheduleActionType.SCHEDULE_UPDATE) {
            return;
        }
        if (event.schedules().isEmpty()) {
            return;
        }

        SchedulesModel target = event.schedules().get(0);
        try {
            if (event.actionType() == ScheduleActionType.SCHEDULE_CREATED) {
                notificationInterfaces.createReminder(target); // DELETE 없이 INSERT만
            } else {
                notificationInterfaces.upsertReminder(target); // 기존 DELETE+INSERT
            }
        } catch (Exception e) {
            log.error("[리마인더 생성 실패] AFTER_COMMIT이라 자동 재시도 없음 - scheduleId={}, memberId={}, error={}",
                    target.getId(), target.getMemberId(), e.getMessage(), e);
        }
    }

}
