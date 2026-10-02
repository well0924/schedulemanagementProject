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

    // 리마인더는 Outbox와 같은 트랜잭션(BEFORE_COMMIT)에서 저장한다 (2026-10-03).
    // 이전 AFTER_COMMIT + REQUIRES_NEW 방식은 원래 커넥션을 반납하기 전에 커넥션을 하나 더 요청해,
    // 요청 하나가 커넥션 2개를 잡았다. 490VU에서 풀이 바닥나 15초 타임아웃과 502가 발생했다.
    // 생성은 INSERT 1건이라 트랜잭션 안에 둬도 부담이 작고, 일정과 리마인더가 함께 커밋/롤백된다.
    // 기존 direct-call 동작과 동일하게 첫 번째 스케줄에 대해서만 리마인더를 생성한다.
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleReminderRegistration(ScheduleDomainEvent event) {
        if (event.actionType() != ScheduleActionType.SCHEDULE_CREATED
                && event.actionType() != ScheduleActionType.SCHEDULE_UPDATE) {
            return;
        }
        if (event.schedules().isEmpty()) {
            return;
        }

        // 같은 트랜잭션이라 예외를 삼키지 않는다. 실패하면 일정 저장도 함께 롤백된다.
        SchedulesModel target = event.schedules().get(0);
        if (event.actionType() == ScheduleActionType.SCHEDULE_CREATED) {
            notificationInterfaces.createReminder(target); // DELETE 없이 INSERT만
        } else {
            notificationInterfaces.upsertReminder(target); // 기존 DELETE+INSERT
        }
    }

}
