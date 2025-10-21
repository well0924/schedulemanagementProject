package com.example.service.schedule;

import com.example.enumerate.schedules.*;
import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.model.schedules.SchedulesModel;
import com.example.security.config.SecurityUtil;
import com.example.service.schedule.repeat.create.RepeatScheduleFactory;
import com.example.service.schedule.repeat.delete.RepeatDeleteRegistry;
import com.example.service.schedule.repeat.update.RepeatUpdateRegistry;
import com.example.service.schedule.support.AttachBinder;
import com.example.service.schedule.support.DomainEventPublisher;
import com.example.service.schedule.guard.ScheduleGuard;
import com.example.service.schedule.support.ScheduleClassifier;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SchedulesModel saveSchedule(SchedulesModel model) {
        // 반복 없음이면 그냥 1건만 저장
        List<SchedulesModel> schedulesToSave = (model.getRepeatType() == RepeatType.NONE || model.getRepeatCount() == null || model.getRepeatCount() <= 0)
                        ? List.of(model)
                        : repeatScheduleFactory.generateRepeatedSchedules(model);

        if (schedulesToSave.isEmpty()) {
            throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_CREATED_FAIL);
        }

        List<SchedulesModel> savedSchedules = new ArrayList<>();

        for (SchedulesModel m : schedulesToSave) {
            //일정 저장
            SchedulesModel saved = saveSingleSchedule(m,model,savedSchedules.isEmpty());
            savedSchedules.add(saved);
        }
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
        NotificationChannel channel = domainEventPublisher.resolveChannel(firstSchedule.getMemberId());
        log.info(channel.toString());
        domainEventPublisher.publishScheduleEvent(firstSchedule,ScheduleActionType.SCHEDULE_CREATED,channel);

        return firstSchedule;
    }

    //일정 수정
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SchedulesModel updateSchedule(Long scheduleId, SchedulesModel model,RepeatUpdateType updateType) {
        SchedulesModel existing = scheduleRepositoryPort.findById(scheduleId);
        //사용자 인증
        scheduleGuard.assertOwnerOrAdmin(existing);
        log.info("수정 요청 들어옴. scheduleId = {}", scheduleId);
        RepeatUpdateType t = Optional.ofNullable(updateType).orElse(RepeatUpdateType.SINGLE);
        // RepeatUpdateType에 따른 일정 수정
        return repeatUpdateRegistry.dispatch(t, existing, model);
    }

    public PROGRESS_STATUS updateProgressStatus(Long scheduleId, PROGRESS_STATUS newStatus) {
        scheduleRepositoryPort.updateStatusOnly(scheduleId, newStatus);
        return newStatus;
    }

    //일정 삭제 (논리 삭제)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteSchedule(Long scheduleId, DeleteType deleteType) {
        SchedulesModel target = scheduleRepositoryPort.findById(scheduleId);
        //삭제시 인증
        scheduleGuard.assertOwnerOrAdmin(target);
        //타입에 따른 일정 삭제
        repeatDeleteRegistry.dispatch(deleteType,target);
        //삭제후 이벤트 발행.
        NotificationChannel channel = domainEventPublisher.resolveChannel(target.getMemberId());
        domainEventPublisher.publishScheduleEvent(target, ScheduleActionType.SCHEDULE_DELETE,channel);
    }

    //선택 삭제
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteSchedules(List<Long> ids) {
        //사용자 인증
        Long me = SecurityUtil.currentUserId();
        log.info("memberId:"+me);
        List<Long> owned = scheduleRepositoryPort.findOwnedIds(me,ids);

        if (owned.size() != ids.size()) {
            throw new ScheduleCustomException(ScheduleErrorCode.INVALID_OWNER_FOR_BULK);
        }
        // 일정 삭제
        scheduleRepositoryPort.markAsDeletedByIds(ids);
        // 각 일정마다 이벤트 발행
        for (Long id : ids) {
            SchedulesModel model = scheduleRepositoryPort.findById(id); // 이벤트 정보용
            NotificationChannel channel = domainEventPublisher.resolveChannel(model.getMemberId());
            domainEventPublisher.publishScheduleEvent(model,ScheduleActionType.SCHEDULE_DELETE,channel);
        }
    }

    //일괄삭제 기능 (자정마다 작동이 되게끔 하기)
    @Scheduled(cron = "0 0 0 * * ?")
    public void deleteOldSchedules() {
        LocalDateTime thresholdDate = LocalDateTime.now().minusMonths(1);
        scheduleRepositoryPort.deleteOldSchedules(thresholdDate);
    }

    private SchedulesModel saveSingleSchedule(SchedulesModel schedule, SchedulesModel originalModel, boolean isFirst) {

        ScheduleType type = scheduleClassifier.classify(schedule);
        schedule = schedule.toBuilder()
                .scheduleType(type)
                .memberId(SecurityUtil.currentUserId())
                .build();
        log.info(type.name());
        scheduleRepositoryPort.validateScheduleConflict(schedule);

        SchedulesModel saved = scheduleRepositoryPort.saveSchedule(schedule);

        if (saved == null || saved.getId() == null) {
            throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_CREATED_FAIL);
        }

        if (attachBinder.hasAttachFiles(originalModel) && isFirst) {
            attachBinder.bindToSchedule(originalModel.getAttachIds(), saved.getId());
        }
        return saved;
    }

}
