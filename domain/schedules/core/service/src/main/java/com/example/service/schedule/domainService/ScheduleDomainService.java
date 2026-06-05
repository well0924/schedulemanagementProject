package com.example.service.schedule.domainService;

import com.example.enumerate.schedules.DeleteType;
import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.enumerate.schedules.RepeatUpdateType;
import com.example.model.schedules.SchedulesModel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class ScheduleDomainService {

    private final ScheduleQueryService scheduleQueryService;

    private final ScheduleCreateService scheduleCreateService;

    private final ScheduleUpdateService scheduleUpdateService;

    private final ScheduleDeleteService scheduleDeleteService;

    @Transactional(readOnly = true)
    public List<SchedulesModel> getAllSchedules() {
        return scheduleQueryService.getAllSchedules();
    }

    @Transactional(readOnly = true)
    public List<SchedulesModel> getAllDeletedSchedules() {
        return scheduleQueryService.getAllDeletedSchedules();
    }

    //회원별 일정목록
    @Transactional(readOnly = true)
    public Page<SchedulesModel> getSchedulesByUserFilter(Pageable pageable) {
        return scheduleQueryService.getSchedulesByUserFilter(pageable);
    }

    //카테고리별 일정목록
    @Transactional(readOnly = true)
    public Page<SchedulesModel> getSchedulesByCategoryFilter(String categoryId,Pageable pageable) {
        return scheduleQueryService.getSchedulesByCategoryFilter(categoryId, pageable);
    }

    //일정상태별 일정목록
    @Transactional(readOnly = true)
    public Page<SchedulesModel> getSchedulesByStatus(String status,Pageable pageable) {
        return scheduleQueryService.getSchedulesByStatus(status, pageable);
    }

    //오늘의 일정 조회
    @Transactional(readOnly = true)
    public List<SchedulesModel> findByTodaySchedule(){
        return scheduleQueryService.findByTodaySchedule();
    }

    //일정 단일 조회
    @Transactional(readOnly = true)
    public SchedulesModel findById(Long scheduleId) {
        return scheduleQueryService.findById(scheduleId);
    }

    @Transactional
    public SchedulesModel saveSchedule(SchedulesModel model) {
        return scheduleCreateService.saveSchedule(model);
    }

    @Transactional
    public SchedulesModel updateSchedule(Long scheduleId, SchedulesModel model, RepeatUpdateType updateType) {
        return scheduleUpdateService.updateSchedule(scheduleId, model, updateType);
    }

    @Transactional
    public PROGRESS_STATUS updateProgressStatus(Long scheduleId, PROGRESS_STATUS newStatus) {
        return scheduleUpdateService.updateProgressStatus(scheduleId, newStatus);
    }

    @Transactional
    public void deleteSchedule(Long scheduleId, DeleteType deleteType) {
        scheduleDeleteService.deleteSchedule(scheduleId, deleteType);
    }

    @Transactional
    public void deleteSchedules(List<Long> ids) {
        scheduleDeleteService.deleteSchedules(ids);
    }

    @Transactional
    public void deleteOldSchedules() {
        scheduleDeleteService.deleteOldSchedules();
    }
}
