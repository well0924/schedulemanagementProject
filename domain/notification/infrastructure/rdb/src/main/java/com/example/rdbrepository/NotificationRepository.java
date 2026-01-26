package com.example.rdbrepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification,Long> {

    // 1. 리마인드 알림: scheduledAt 시간이 현재보다 전이고, 아직 안 보낸 알림
    @Query("SELECT n FROM Notification n " +
            "WHERE n.notificationType = 'SCHEDULE_REMINDER'"+
            "AND n.isReminderSent = false " +
            "AND n.scheduledAt <= :now")
    List<Notification> findPendingReminders(@Param("now") LocalDateTime now);

    // 2. 특정 사용자(userId) 알림 조회 (최신순)
    List<Notification> findByUserIdOrderByCreatedTimeDesc(Long userId);

    // 3. 특정 사용자(userId)의 안 읽은 알림 조회
    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedTimeDesc(Long userId);

    // 4. 중복 알림 방지나 기존 알림 상태 조회용
    Notification findByMessageAndUserId(String message,Long userId);

    // 5. 리마인드 알림 삭제(리마인드 알림이 울리고 3일뒤 삭제)
    @Modifying
    @Query("DELETE FROM Notification n " +
            "WHERE n.notificationType = :type " +
            "AND n.isSent = true " +
            "AND n.scheduledAt < :threshold")
    void deleteOldSentReminders(@Param("type") String type,
                                @Param("threshold") LocalDateTime threshold);

    // 6. 특정 일정(scheduleId)에 해당하는 리마인드 알림
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.scheduleId = :scheduleId AND n.notificationType = 'SCHEDULE_REMINDER'")
    void deleteReminderByScheduleId(@Param("scheduleId") Long scheduleId);

    // 7.메시지 알림 전송 true로 변경.
    @Modifying
    @Query("UPDATE Notification n SET n.isSent = true WHERE n.id = :id")
    void markAsSent(@Param("id") Long id);
    
    // 8.리마인드 알림 전송 true 변경
    @Modifying
    @Query("update Notification n set n.isReminderSent = true where n.id = :id")
    void markAsReminderSent(@Param("id")  Long id);
}
