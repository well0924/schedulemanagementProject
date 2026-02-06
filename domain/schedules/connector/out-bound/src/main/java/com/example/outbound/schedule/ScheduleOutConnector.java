package com.example.outbound.schedule;

import com.example.category.dto.CategoryErrorCode;
import com.example.category.exception.CategoryCustomException;
import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.enumerate.schedules.ScheduleType;
import com.example.exception.dto.MemberErrorCode;
import com.example.exception.exception.MemberCustomException;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.model.schedules.SchedulesModel;
import com.example.rdb.CategoryRepository;
import com.example.rdb.member.MemberRepository;
import com.example.rdbrepository.*;
import com.example.schedules.mapper.ScheduleEntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleOutConnector implements ScheduleRepositoryPort {

    private final ScheduleRepository scheduleRepository;

    private final MemberRepository memberRepository;

    private final CategoryRepository categoryRepository;

    private final ScheduleEntityMapper scheduleEntityMapper;

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
                .map(scheduleEntityMapper::toModel)
                .collect(Collectors.toList());
        if(result.isEmpty()) {
            throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_EMPTY);
        }
        return result;
    }

    //회원 아이디로 일정목록
    public Page<SchedulesModel> findByUserId(String  userId,Pageable pageable) {
        return Optional.ofNullable(scheduleRepository.findAllByUserId(userId,pageable))
                .filter(list->!list.isEmpty())
                .orElseThrow(()->new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_EMPTY));
    }
    
    //회원 번호로 일정목록
    public Page<SchedulesModel> findAllByMemberId(Long memberId, Pageable pageable) {
        return Optional
                .ofNullable(scheduleRepository.findAllByMemberId(memberId,pageable))
                .filter(list ->!list.isEmpty())
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

    public List<SchedulesModel> findByRepeatGroupId(String repeatGroupId) {
        return scheduleRepository.findByRepeatGroupId(repeatGroupId)
                .stream()
                .map(scheduleEntityMapper::toModel)
                .collect(Collectors.toList());
    }

    public List<SchedulesModel> findAfterStartTime(String repeatGroupId,LocalDateTime startTime) {
        return scheduleRepository.findByRepeatGroupIdAndStartTimeAfter(repeatGroupId,startTime)
                .stream()
                .map(scheduleEntityMapper::toModel)
                .collect(Collectors.toList());
    }
    
    

    //일정 단일 조회 (첨부파일 포함)
    public SchedulesModel findById(Long scheduleId) {
        return Optional.ofNullable(scheduleRepository.findByScheduleId(scheduleId))
                .orElseThrow(()-> new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
    }

    //오늘일정 목록 보여주기.
    public List<SchedulesModel> findByTodaySchedule(Long userId){
        LocalDateTime today = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        List<String> statusList = List.of("IN_COMPLETE", "PROGRESS");

        return scheduleRepository.findTodayActiveSchedules(userId,today,statusList)
                .stream()
                .map(scheduleEntityMapper::toModel)
                .collect(Collectors.toList());
    }

    //일정저장
    public SchedulesModel saveSchedule(SchedulesModel model) {
        validateScheduleData(model);
        return scheduleEntityMapper.toModel(
                scheduleRepository.save(scheduleEntityMapper.toEntity(model)));
    }

    //일정 수정
    public SchedulesModel updateSchedule(Long scheduleId, SchedulesModel model) {
        Schedules schedules = getScheduleById(scheduleId);
        validateScheduleData(model);
        model.updateProgressStatus();
        schedules.updateSchedule(model);
        return scheduleEntityMapper.toModel(schedules);
    }
    
    //일정상태 변경
    public void updateStatusOnly(Long scheduleId, PROGRESS_STATUS status) {
        // enum → 문자열로 변환하여 전달
        scheduleRepository.updateProgressStatus(scheduleId, status.name());
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
        // 1. 시작시간 < 종료시간 확인
        if (!model.getStartTime().isBefore(model.getEndTime())) {
            throw new ScheduleCustomException(ScheduleErrorCode.INVALID_TIME_RANGE);
        }

        // 2. 하루 종일 일정이면 날짜 단위 충돌만 검사하고 종료
        if (Boolean.TRUE.equals(model.isAllDay())) {
            validateAllDayScheduleConflict(model);
            return; // 시간대 검사 안하게 바로 종료
        }

        // 3. 시간대 충돌 검사
        if (model.getScheduleType() == ScheduleType.SINGLE_DAY) {
            Long conflictCount = scheduleRepository.countOverlappingSchedules(
                    model.getMemberId(),
                    model.getStartTime(),
                    model.getEndTime(),
                    model.getId() // 수정이면 자기 자신 제외
            );

            if (conflictCount != null && conflictCount > 0) {
                throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_TIME_CONFLICT);
            }
        }
    }

    public void validateAllDayScheduleConflict(SchedulesModel model) {
        LocalDate date = model.getStartTime().toLocalDate();
        Long count = scheduleRepository.countAllDayOnDate(model.getMemberId(), date);

        if (count != null && count > 0) {
            throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_TIME_CONFLICT);
        }
    }

    // 선택 일정 삭제시 사용자 번호(userId) 인증
    public List<Long> findOwnedIds(Long me , List<Long> ids) {
        return scheduleRepository.findOwnedIds(me,ids);
    }

    private void validateScheduleData(SchedulesModel model) {
        if (!memberRepository.existsById(model.getMemberId())) {
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

}
