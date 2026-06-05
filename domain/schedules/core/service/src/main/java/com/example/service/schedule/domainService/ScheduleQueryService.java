package com.example.service.schedule.domainService;

import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.model.schedules.SchedulesModel;
import com.example.security.config.SecurityUtil;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
@Transactional(readOnly = true)
public class ScheduleQueryService {

    private final ScheduleRepositoryPort scheduleRepositoryPort;

    public List<SchedulesModel> getAllSchedules() {
        return scheduleRepositoryPort.findAllSchedules();
    }

    public List<SchedulesModel> getAllDeletedSchedules() {
        return scheduleRepositoryPort.findAllByIsDeletedScheduled();
    }

    //회원별 일정목록
    public Page<SchedulesModel> getSchedulesByUserFilter(Pageable pageable) {
        return scheduleRepositoryPort.findByUserId(SecurityUtil.currentUserName(),pageable);
    }

    //카테고리별 일정목록
    public Page<SchedulesModel> getSchedulesByCategoryFilter(String categoryId,Pageable pageable) {
        return scheduleRepositoryPort.findByCategoryId(categoryId,pageable);
    }

    //일정상태별 일정목록
    public Page<SchedulesModel> getSchedulesByStatus(String status,Pageable pageable) {
        return scheduleRepositoryPort.findAllByPROGRESS_STATUS(SecurityUtil.currentUserName(),status,pageable);
    }

    //오늘의 일정 조회
    public List<SchedulesModel> findByTodaySchedule(){
        return scheduleRepositoryPort.findByTodaySchedule(SecurityUtil.currentUserId());
    }

    //일정 단일 조회
    public SchedulesModel findById(Long scheduleId) {
        return scheduleRepositoryPort.findById(scheduleId);
    }
}
