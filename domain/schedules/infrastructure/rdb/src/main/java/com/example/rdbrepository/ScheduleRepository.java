package com.example.rdbrepository;

import com.example.rdbrepository.custom.ScheduleRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    @Query("SELECT COUNT(s) FROM Schedules s " +
            "WHERE s.userId = :userId " +
            "AND s.startTime < :endTime " +
            "AND s.endTime > :startTime " +
            "AND (:excludeId IS NULL OR s.id != :excludeId)")
    Long countOverlappingSchedules(@Param("userId") Long userId,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   @Param("excludeId") Long excludeId);

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
}
