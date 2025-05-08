package com.example.outbound.schedule;

import com.example.category.dto.CategoryErrorCode;
import com.example.category.exception.CategoryCustomException;
import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.enumerate.schedules.RepeatType;
import com.example.exception.dto.MemberErrorCode;
import com.example.exception.exception.MemberCustomException;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import com.example.model.schedules.SchedulesModel;
import com.example.rdb.CategoryRepository;
import com.example.rdb.member.MemberRepository;
import com.example.rdbrepository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Transactional
@RequiredArgsConstructor
public class ScheduleOutConnector {

    private final ScheduleRepository scheduleRepository;

    private final MemberRepository memberRepository;

    private final CategoryRepository categoryRepository;

    //일정 전체목록
    public List<SchedulesModel> findAllSchedules(){
        List<SchedulesModel> result = scheduleRepository.findAllSchedule();

        if(result.isEmpty()) {
            throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_EMPTY);
        }

        return result;
    }

    //일정 전체 목록(삭제여부가 된 일정)
    public List<SchedulesModel> findAllByIsDeletedScheduled() {
        List<SchedulesModel> result = scheduleRepository
                .findAllByIsDeletedScheduled()
                .stream()
                .map(this::toModel)
                .collect(Collectors.toList());
        if(result.isEmpty()) {
            throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_EMPTY);
        }
        return result;
    }

    //회원 번호로 일정목록
    public Page<SchedulesModel> findByUserId(String  userId,Pageable pageable) {
        return Optional.ofNullable(scheduleRepository.findAllByUserId(userId,pageable))
                .filter(list->!list.isEmpty())
                .orElseThrow(()->new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_EMPTY));
    }

    //카테고리 번호로 일정목록
    public Page<SchedulesModel> findByCategoryId(String categoryName,Pageable pageable) {
        return Optional.ofNullable(scheduleRepository.findAllByCategoryName(categoryName,pageable))
                .filter(list -> !list.isEmpty())
                .orElseThrow(()->new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_EMPTY));
    }

    //회원 일정상태별 일정목록
    public Page<SchedulesModel> findAllByPROGRESS_STATUS(String userId,String progressStatus,Pageable pageable) {
        return Optional
                .ofNullable(scheduleRepository.findAllByProgressStatus(userId,progressStatus,pageable))
                .filter(list->!list.isEmpty())
                .orElseThrow(()->new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_EMPTY));
    }

    //일정 단일 조회 (첨부파일 포함)
    public SchedulesModel findById(Long scheduleId) {
        return Optional.ofNullable(scheduleRepository.findByScheduleId(scheduleId))
                .orElseThrow(()-> new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
    }

    //오늘일정 목록 보여주기.
    public List<SchedulesModel> findByTodaySchedule(Long userId){
        return scheduleRepository.findTodaySchedules(userId)
                .stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    //일정저장
    public SchedulesModel saveSchedule(SchedulesModel model) {
        validateScheduleData(model);

        Schedules schedules = Schedules
                .builder()
                .contents(model.getContents())
                .scheduleDay(model.getScheduleDays())
                .scheduleMonth(model.getScheduleMonth())
                .startTime(model.getStartTime())
                .endTime(model.getEndTime())
                .userId(model.getUserId())
                .categoryId(model.getCategoryId())
                .isDeletedScheduled(false)
                .progress_status(String.valueOf(model.getProgressStatus()))
                .repeatType(String.valueOf(model.getRepeatType()))
                .repeatCount(model.getRepeatCount())
                .repeatInterval(model.getRepeatInterval())
                .build();

        return toModel(scheduleRepository.save(schedules));
    }

    //일정 수정
    public SchedulesModel updateSchedule(Long scheduleId, SchedulesModel model) {
        Schedules schedules = getScheduleById(scheduleId);

        validateScheduleData(model);

        LocalDateTime now = LocalDateTime.now();
        PROGRESS_STATUS newStatus;

        if (model.getEndTime().isBefore(now)) {
            newStatus = PROGRESS_STATUS.COMPLETE;
        } else if (model.getStartTime().isBefore(now)) {
            newStatus = PROGRESS_STATUS.PROGRESS;
        } else {
            newStatus = PROGRESS_STATUS.IN_COMPLETE;
        }

        Schedules updatedSchedules = schedules.toBuilder()
                .contents(model.getContents())
                .scheduleDay(model.getScheduleDays())
                .scheduleMonth(model.getScheduleMonth())
                .progress_status(newStatus.name())  // 상태 업데이트 반영
                .startTime(model.getStartTime())
                .endTime(model.getEndTime())
                .categoryId(model.getCategoryId())
                .userId(model.getUserId())
                .repeatType(String.valueOf(model.getRepeatType()))
                .repeatCount(model.getRepeatCount())
                .repeatInterval(model.getRepeatInterval())
                .build();

        return toModel(scheduleRepository.save(updatedSchedules)); // 일정 저장 후 모델 변환
    }

    //일정 삭제(논리삭제)
    public void deleteSchedule(Long scheduleId) {
        Schedules schedules = getScheduleById(scheduleId);
        schedules.isDeletedScheduled();
        scheduleRepository.save(schedules);
    }

    //일정 삭제(벌크처리)
    public void markAsDeletedByIds(List<Long> ids) {
        scheduleRepository.markAsDeletedByIds(ids);
    }

    //스케줄러를 사용을해서 일괄적으로 일정을 삭제.
    public void deleteOldSchedules(LocalDateTime thresholdDate) {
        scheduleRepository.deleteOldSchedules(thresholdDate);
    }

    public void markAsDeletedByRepeatGroupId(String repeatGroupId) {
        scheduleRepository.markAsDeletedByRepeatGroupId(repeatGroupId);
    }

    public void markAsDeletedAfter(String repeatGroupId, LocalDateTime startTime) {
        scheduleRepository.markAsDeletedAfter(repeatGroupId,startTime);
    }

    //일정 충돌 확인
    public void validateScheduleConflict(SchedulesModel model) {
        Long conflictCount = scheduleRepository.countOverlappingSchedules(
                model.getUserId(),
                model.getStartTime(),
                model.getEndTime(),
                model.getId() // 수정이면 자기 자신 제외
        );

        if (conflictCount != null && conflictCount > 0) {
            throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_TIME_CONFLICT);
        }
    }

    private void validateScheduleData(SchedulesModel model) {
        if (!memberRepository.existsById(model.getUserId())) {
            throw new MemberCustomException(MemberErrorCode.NOT_FIND_USERID);
        }
        if (!categoryRepository.existsById(model.getCategoryId())) {
            throw new CategoryCustomException(CategoryErrorCode.INVALID_PARENT_CATEGORY);
        }
        if (model.getStartTime().isAfter(model.getEndTime())) {
            throw new ScheduleCustomException(ScheduleErrorCode.START_TIME_AFTER_END_TIME_EXCEPTION);
        }
    }

    private Schedules getScheduleById(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
    }

    private SchedulesModel toModel(Schedules schedules) {
        return SchedulesModel
                .builder()
                .id(schedules.getId())
                .contents(schedules.getContents())
                .scheduleDays(schedules.getScheduleDay())
                .scheduleMonth(schedules.getScheduleMonth())
                .progressStatus(PROGRESS_STATUS.valueOf(schedules.getProgress_status()))
                .startTime(schedules.getStartTime())
                .endTime(schedules.getEndTime())
                .categoryId(schedules.getCategoryId())
                .userId(schedules.getUserId())
                .repeatType(RepeatType.valueOf(schedules.getRepeatType()))//일정 반복 유형
                .repeatCount(schedules.getRepeatCount())//반복횟수
                .repeatInterval(schedules.getRepeatInterval())
                .createdBy(schedules.getCreatedBy())
                .createdTime(schedules.getCreatedTime())
                .updatedBy(schedules.getUpdatedBy())
                .updatedTime(schedules.getUpdatedTime())
                .build();
    }
}
