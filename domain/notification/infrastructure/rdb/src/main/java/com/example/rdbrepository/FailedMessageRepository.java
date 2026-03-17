package com.example.rdbrepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FailedMessageRepository extends JpaRepository<FailedMessage,Long> {

    // 아직 해결되지 않은 모든 실패 메시지 조회
    List<FailedMessage> findByResolvedFalse();

    // 고유한 eventId로 존재 여부를 파악
    boolean existsByEventId(String eventId);

    // 성공한 지 오래된(threshold 이전) 데이터를 삭제하여 DB 용량 확보
    @Modifying
    @Query("DELETE FROM FailedMessage e WHERE e.resolved = true AND e.resolvedAt < :threshold")
    int deleteByResolvedIsTrueAndResolvedAtBefore(@Param("threshold") LocalDateTime threshold);

    // resolved = false (미완료) + dead = false (포기하지 않은 건) + nextRetryTime <= now (실행 시간이 도래한 건)
    List<FailedMessage> findByResolvedFalseAndDeadFalseAndNextRetryTimeBefore(LocalDateTime now);

    // 특정 이벤트 ID로 실패 기록 조회 (수동 복구 시 사용)
    Optional<FailedMessage> findByEventId(String eventId);
}
