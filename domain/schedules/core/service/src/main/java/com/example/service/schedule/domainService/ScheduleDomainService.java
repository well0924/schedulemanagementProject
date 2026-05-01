package com.example.service.schedule.domainService;

import com.example.enumerate.schedules.*;
import com.example.events.enums.AggregateType;
import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.events.kafka.NotificationEvents;
import com.example.events.outbox.OutboxEventService;
import com.example.events.spring.ScheduleEvents;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.interfaces.notification.notification.NotificationInterfaces;
import com.example.model.schedules.SchedulesModel;
import com.example.security.config.SecurityUtil;
import com.example.service.schedule.domainService.repeat.create.RepeatScheduleFactory;
import com.example.service.schedule.domainService.repeat.delete.RepeatDeleteRegistry;
import com.example.service.schedule.domainService.repeat.update.RepeatUpdateRegistry;
import com.example.service.schedule.domainService.support.AttachBinder;
import com.example.service.schedule.domainService.support.DomainEventPublisher;
import com.example.service.schedule.domainService.guard.ScheduleGuard;
import com.example.service.schedule.domainService.support.ScheduleClassifier;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@Transactional
@AllArgsConstructor
public class ScheduleDomainService {

    private final ScheduleRepositoryPort scheduleRepositoryPort;

    private final DomainEventPublisher domainEventPublisher;

    private final AttachBinder attachBinder;

    private final ScheduleGuard scheduleGuard;

    private final ScheduleClassifier scheduleClassifier;

    private final RepeatUpdateRegistry repeatUpdateRegistry;

    private final RepeatDeleteRegistry repeatDeleteRegistry;

    private final RepeatScheduleFactory repeatScheduleFactory;

    private final OutboxEventService outboxEventService;

    private final NotificationInterfaces notificationInterfaces;

    public List<SchedulesModel> getAllSchedules() {
        return scheduleRepositoryPort.findAllSchedules();
    }

    public List<SchedulesModel> getAllDeletedSchedules() {
        return scheduleRepositoryPort.findAllByIsDeletedScheduled();
    }

    //회원별 일정목록
    @Transactional(readOnly = true)
    public Page<SchedulesModel> getSchedulesByUserFilter(Pageable pageable) {
        return scheduleRepositoryPort.findByUserId(SecurityUtil.currentUserName(),pageable);
    }

    //카테고리별 일정목록
    @Transactional(readOnly = true)
    public Page<SchedulesModel> getSchedulesByCategoryFilter(String categoryId,Pageable pageable) {
        return scheduleRepositoryPort.findByCategoryId(categoryId,pageable);
    }

    //일정상태별 일정목록
    @Transactional(readOnly = true)
    public Page<SchedulesModel> getSchedulesByStatus(String status,Pageable pageable) {
        return scheduleRepositoryPort.findAllByPROGRESS_STATUS(SecurityUtil.currentUserName(),status,pageable);
    }

    //오늘의 일정 조회
    @Transactional(readOnly = true)
    public List<SchedulesModel> findByTodaySchedule(){
        return scheduleRepositoryPort.findByTodaySchedule(SecurityUtil.currentUserId());
    }

    //일정 단일 조회
    @Transactional(readOnly = true)
    public SchedulesModel findById(Long scheduleId) {
        return scheduleRepositoryPort.findById(scheduleId);
    }

    //일정 등록
    @Transactional
    public SchedulesModel saveSchedule(SchedulesModel model) {
        // 반복 없음이면 그냥 1건만 저장
        List<SchedulesModel> schedulesToSave = (model.getRepeatType() == RepeatType.NONE || model.getRepeatCount() == null || model.getRepeatCount() <= 0)
                        ? List.of(model)
                        : repeatScheduleFactory.generateRepeatedSchedules(model);

        if (schedulesToSave.isEmpty()) {
            throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_CREATED_FAIL);
        }

        Long currentMemberId = SecurityUtil.currentUserId();

        List<SchedulesModel> processedSchedules = schedulesToSave.stream()
                .map(m -> m.toBuilder()
                        .scheduleType(scheduleClassifier.classify(m))
                        .memberId(currentMemberId)
                        .build())
                .sorted(Comparator.comparing(SchedulesModel::getStartTime)) // 인덱스에 있는 시작시간으로 정렬
                .toList();

        // 일정 생성시 검증
        validateBulkConflict(processedSchedules);
        scheduleGuard.validateCreation(processedSchedules);
        // 일정 일괄 저장
        List<SchedulesModel> savedSchedules = scheduleRepositoryPort.saveAll(processedSchedules);

        // 첫 번째 등록된 일정 반환
        SchedulesModel firstSchedule = savedSchedules.get(0);
        log.info("result:"+firstSchedule);
        //attachId 바인딩.
        if (model.getAttachIds() != null && !model.getAttachIds().isEmpty()) {
            attachBinder.bindToSchedule(model.getAttachIds(),firstSchedule.getId());
            firstSchedule = firstSchedule.toBuilder()
                    .attachIds(model.getAttachIds())
                    .build();
        }
        log.info("일정 저장 완료, 이벤트 발행 시도");
        // 이벤트 발행.
        sendCreateNotification(firstSchedule);
        return firstSchedule;
    }

    //일정 수정
    @Transactional
    public SchedulesModel updateSchedule(Long scheduleId, SchedulesModel model,RepeatUpdateType updateType) {
        SchedulesModel existing = scheduleRepositoryPort.findById(scheduleId);
        //사용자 인증
        scheduleGuard.assertOwnerOrAdmin(existing);
        log.info("수정 요청 들어옴. scheduleId = {}", scheduleId);
        RepeatUpdateType t = Optional.ofNullable(updateType).orElse(RepeatUpdateType.SINGLE);
        // RepeatUpdateType에 따른 일정 수정
        List<SchedulesModel> result = repeatUpdateRegistry.dispatch(t, existing, model);
        // 수정시 리마인드 알림
        notificationInterfaces.createReminder(result.get(0));

        List<Object> eventDtos = new ArrayList<>();
        List<String> aggregateIds = new ArrayList<>();
        List<String> eventTypes = new ArrayList<>();

        for (SchedulesModel updated : result) {
            NotificationChannel channel = domainEventPublisher.resolveChannel(updated.getMemberId());
            NotificationEvents event = NotificationEvents.of(ScheduleEvents.builder()
                    .scheduleId(updated.getId())
                    .startTime(updated.getStartTime())
                    .contents(updated.getContents())
                    .userId(updated.getMemberId())
                    .notificationChannel(channel)
                    .notificationType(ScheduleActionType.SCHEDULE_UPDATE)
                    .createdTime(updated.getCreatedTime())
                    .build());

            eventDtos.add(event);
            aggregateIds.add(updated.getId().toString());
            eventTypes.add(ScheduleActionType.SCHEDULE_UPDATE.name());
        }
        // 2. 벌크 저장 호출
        outboxEventService.saveAllEvents(eventDtos, AggregateType.SCHEDULE.name(), aggregateIds, eventTypes);
        return result.get(0);
    }

    public PROGRESS_STATUS updateProgressStatus(Long scheduleId, PROGRESS_STATUS newStatus) {
        scheduleRepositoryPort.updateStatusOnly(scheduleId, newStatus);
        return newStatus;
    }

    //일정 삭제 (논리 삭제)
    @Transactional
    public void deleteSchedule(Long scheduleId, DeleteType deleteType) {
        SchedulesModel target = scheduleRepositoryPort.findById(scheduleId);
        log.info("id:"+target.getId());
        //삭제시 인증
        scheduleGuard.assertOwnerOrAdmin(target);
        //타입에 따른 일정 삭제
        List<SchedulesModel> deletedSchedules = repeatDeleteRegistry.dispatch(deleteType,target);
        // 일정 자체가 삭제되었으므로 예약된 알림도 DB에서 완전히 제거합니다.
        notificationInterfaces.deleteReminderByScheduleId(scheduleId);

        List<Object> eventDtos = new ArrayList<>();
        List<String> aggregateIds = new ArrayList<>();
        List<String> eventTypes = new ArrayList<>();

        for (SchedulesModel model : deletedSchedules) {
            NotificationChannel channel = domainEventPublisher.resolveChannel(model.getMemberId());
            NotificationEvents event = NotificationEvents.of(ScheduleEvents.builder()
                    .scheduleId(model.getId())
                    .startTime(model.getStartTime())
                    .contents(model.getContents())
                    .userId(model.getMemberId())
                    .notificationChannel(channel)
                    .notificationType(ScheduleActionType.SCHEDULE_DELETE)
                    .createdTime(model.getCreatedTime())
                    .build());

            eventDtos.add(event);
            aggregateIds.add(model.getId().toString());
            eventTypes.add(ScheduleActionType.SCHEDULE_DELETE.name());
        }

        // 삭제된 모든 일정에 대해 Outbox 이벤트 원샷 저장
        outboxEventService.saveAllEvents(eventDtos, AggregateType.SCHEDULE.name(), aggregateIds, eventTypes);
    }

    //선택 삭제
    @Transactional
    public void deleteSchedules(List<Long> ids) {
        //사용자 인증
        Long me = SecurityUtil.currentUserId();
        log.info("memberId:"+me);
        // 삭제할 일정 번호 찾기
        List<Long> owned = scheduleRepositoryPort.findOwnedIds(me,ids);

        if (owned.size() != ids.size()) {
            throw new ScheduleCustomException(ScheduleErrorCode.INVALID_OWNER_FOR_BULK);
        }

        List<SchedulesModel> targets = scheduleRepositoryPort.findAllByIds(ids);
        // 일정 삭제
        scheduleRepositoryPort.markAsDeletedByIds(ids);


        List<Object> eventDtos = new ArrayList<>();
        List<String> aggregateIds = new ArrayList<>();
        List<String> eventTypes = new ArrayList<>();

        for (SchedulesModel model : targets) {
            NotificationChannel channel = domainEventPublisher.resolveChannel(model.getMemberId());
            NotificationEvents event = NotificationEvents.of(ScheduleEvents.builder()
                    .scheduleId(model.getId())
                    .startTime(model.getStartTime())
                    .contents(model.getContents())
                    .userId(model.getMemberId())
                    .notificationChannel(channel)
                    .notificationType(ScheduleActionType.SCHEDULE_DELETE)
                    .createdTime(model.getCreatedTime())
                    .build());

            eventDtos.add(event);
            aggregateIds.add(model.getId().toString());
            eventTypes.add(ScheduleActionType.SCHEDULE_DELETE.name());
        }

        // 5. [벌크 Outbox 저장] 쿼리 1번으로 끝
        outboxEventService.saveAllEvents(
                eventDtos,
                AggregateType.SCHEDULE.name(),
                aggregateIds,
                eventTypes
        );
    }

    //일괄삭제 기능 (자정마다 작동이 되게끔 하기)
    @Scheduled(cron = "0 0 0 * * ?")
    public void deleteOldSchedules() {
        LocalDateTime thresholdDate = LocalDateTime.now().minusMonths(1);
        scheduleRepositoryPort.deleteOldSchedules(thresholdDate);
    }

    // 벌크 충돌 검증 로직 상세
    private void validateBulkConflict(List<SchedulesModel> newSchedules) {
        // 전체 기간 산출
        LocalDateTime minStart = newSchedules.stream().map(SchedulesModel::getStartTime).min(LocalDateTime::compareTo).get();
        LocalDateTime maxEnd = newSchedules.stream().map(SchedulesModel::getEndTime).max(LocalDateTime::compareTo).get();

        // 1번의 쿼리로 해당 범위의 기존 일정 모두 가져오기
        long isConflictCount = scheduleRepositoryPort.findOverlappingSchedulesInRange(
                newSchedules.get(0).getMemberId(), minStart, maxEnd);

        // 충돌 체크
        if (isConflictCount > 0) throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_TIME_CONFLICT);
    }

    // 일정 생성시 리마인드 알림 및 이벤트 발행
    private void sendCreateNotification(SchedulesModel firstSchedule) {
        // 리마인드 알림 생성
        notificationInterfaces.createReminder(firstSchedule);

        // 이벤트 발행 (Outbox 저장)
        NotificationChannel channel = domainEventPublisher.resolveChannel(firstSchedule.getMemberId());
        NotificationEvents notificationEvents = NotificationEvents.of(ScheduleEvents.builder()
                .scheduleId(firstSchedule.getId())
                .startTime(firstSchedule.getStartTime())
                .contents(firstSchedule.getContents())
                .userId(firstSchedule.getMemberId())
                .notificationChannel(channel)
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .createdTime(firstSchedule.getCreatedTime())
                .build());

        outboxEventService.saveEvent(
                notificationEvents,
                AggregateType.SCHEDULE.name(),
                firstSchedule.getId().toString(),
                notificationEvents.getNotificationType().name()
        );
    }
}
