package com.example.rdbrepository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification,Long> {

    // 1. 리마인드 알림: scheduledAt 시간이 현재보다 전이고, 아직 안 보낸 알림
    List<Notification> findByScheduledAtBeforeAndIsSentFalse(LocalDateTime now);

    // 2. 특정 사용자(userId) 알림 조회 (최신순)
    List<Notification> findByUserIdOrderByCreatedTimeDesc(Long userId);

    // 3. 특정 사용자(userId)의 안 읽은 알림 조회
    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedTimeDesc(Long userId);

    Notification findByMessageAndUserId(String message,Long userId);
}
