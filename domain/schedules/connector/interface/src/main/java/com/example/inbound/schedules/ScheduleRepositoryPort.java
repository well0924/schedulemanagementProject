package com.example.inbound.schedules;

import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.model.schedules.SchedulesModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleRepositoryPort {

    List<SchedulesModel> findAllSchedules();

    List<SchedulesModel> findAllByIsDeletedScheduled();

    Page<SchedulesModel> findByUserId(String userId, Pageable pageable);

    Page<SchedulesModel> findAllByMemberId(Long memberId, Pageable pageable);

    Page<SchedulesModel> findByCategoryId(String categoryId, Pageable pageable);

    Page<SchedulesModel> findAllByPROGRESS_STATUS(String userId, String status, Pageable pageable);

    SchedulesModel findById(Long id);

    SchedulesModel saveSchedule(SchedulesModel model);

    SchedulesModel updateSchedule(Long id, SchedulesModel model);

    List<SchedulesModel> findByRepeatGroupId(String repeatGroupId);

    List<SchedulesModel> findAfterStartTime(String repeatGroupId,LocalDateTime startTime);

    List<SchedulesModel> findByTodaySchedule(Long userId);

    void updateStatusOnly(Long id, PROGRESS_STATUS status);

    void deleteSchedule(Long id);

    void markAsDeletedByIds(List<Long> ids);

    void markAsDeletedAfter(String repeatGroupId, LocalDateTime startTime);

    void markAsDeletedByRepeatGroupId(String repeatGroupId);

    void deleteOldSchedules(LocalDateTime thresholdDate);

    void validateScheduleConflict(SchedulesModel model);

    List<Long> findOwnedIds(Long memberId, List<Long> ids);
}
