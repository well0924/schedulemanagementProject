package com.example.rdbrepository.custom;

import com.example.model.schedules.SchedulesModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ScheduleRepositoryCustom {
    //일정 전체목록
    List<SchedulesModel> findAllSchedule();
    //일정 단일조회
    SchedulesModel findByScheduleId(Long scheduleId);
    //회원별 일정목록
    Page<SchedulesModel>findAllByUserId(String userId,Pageable pageable);
    //카테고리이름별 일정목록
    Page<SchedulesModel>findAllByCategoryName(String categoryName,Pageable pageable);
    //일정 상태별로 회원일정 목록
    Page<SchedulesModel>findAllByProgressStatus(String userId, String progressStatus, Pageable pageable);

    Page<SchedulesModel> findAllByMemberId(Long memberId, Pageable pageable);
}
