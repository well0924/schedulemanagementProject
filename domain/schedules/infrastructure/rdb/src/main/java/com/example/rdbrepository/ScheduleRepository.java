package com.example.rdbrepository;

import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.rdbrepository.custom.ScheduleRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedules, Long>, ScheduleRepositoryCustom {
    //삭제가 된 일정들
    @Query(value = "select s from Schedules s where s.isDeletedScheduled = true")
    List<Schedules> findAllByIsDeletedScheduled();

    //일정 일괄 삭제
    @Modifying
    @Query("DELETE FROM Schedules s WHERE s.isDeletedScheduled = true AND s.endTime < :thresholdDate")
    int deleteOldSchedules(@Param("thresholdDate") LocalDateTime thresholdDate);

    //일정 충돌 확인
    @Query("""
        SELECT 
            COUNT(s) 
        FROM 
            Schedules s
        WHERE 
            s.userId = :userId
        AND s.scheduleType = 'SINGLE_DAY'
        AND (:startTime < s.endTime AND :endTime > s.startTime)
        AND (:excludeId IS NULL OR s.id != :excludeId)
    """)
    Long countOverlappingSchedules(@Param("userId") Long userId,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   @Param("excludeId") Long excludeId);

    //당일인지 하루종일인지 확인하는 쿼리.
    @Query("""
    SELECT COUNT(s) FROM Schedules s
    WHERE s.userId = :userId
      AND s.isAllDay = true
      AND DATE(s.startTime) = :date
      AND s.isDeletedScheduled = false
    """)
    Long countAllDayOnDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    //일정 삭제 관련
    @Modifying
    @Query("UPDATE Schedules s SET s.isDeletedScheduled = true WHERE s.repeatGroupId = :repeatGroupId")
    void markAsDeletedByRepeatGroupId(@Param("repeatGroupId") String repeatGroupId);

    // 현재 일정 이후만 삭제함
    @Modifying
    @Query("""
        UPDATE Schedules s 
        SET s.isDeletedScheduled = true 
        WHERE s.repeatGroupId = :repeatGroupId 
        AND s.startTime >= :startTime
    """)
    void markAsDeletedAfter(@Param("repeatGroupId") String repeatGroupId, @Param("startTime") LocalDateTime startTime);

    //일정 벌크 삭제
    @Modifying
    @Query("UPDATE Schedules s SET s.isDeletedScheduled = true WHERE s.id IN :ids")
    void markAsDeletedByIds(@Param("ids") List<Long> ids);

    //현재 남아있는 일정 보여주기
    @Query("""
    SELECT 
        s 
    FROM 
        Schedules s
    WHERE s.userId = :userId
      AND s.isDeletedScheduled = false
      AND s.progress_status IN :statusList
      AND s.startTime <= :today
      AND s.endTime >= :today
    """)
    List<Schedules> findTodayActiveSchedules(
            @Param("userId") Long userId,
            @Param("today") LocalDateTime today,
            @Param("statusList") List<String> statusList);

    @Modifying
    @Query("UPDATE Schedules s SET s.progress_status = :status WHERE s.id = :scheduleId")
    void updateProgressStatus(@Param("scheduleId") Long scheduleId, @Param("status") String status);
}
