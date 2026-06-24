package com.example.events.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity,String> {

    // 아직 Kafka로 전송되지 않은 이벤트를 가장 오래된 순서대로 100건 조회.
    @Query("SELECT e FROM OutboxEventEntity e WHERE e.sent = false ORDER BY e.retryCount ASC, e.createdAt ASC")
    List<OutboxEventEntity> findPendingEvents(Pageable pageable);

    /**
     * 발행이 성공(sent=true)하고 특정 보관 기간(threshold)이 지난 데이터를 물리 삭제합니다.
     * @param threshold 삭제 기준 시간 (예: LocalDateTime.now().minusDays(3))
     * @return 삭제된 레코드 수
     */
    int deleteBySentTrueAndCreatedAtBefore(LocalDateTime threshold);

    /**
     * 카프카 발행 성공 시 상태 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OutboxEventEntity e SET e.sent = true, e.sentAt = :sentAt WHERE e.id = :id")
    void markAsSent(@Param("id") String id, @Param("sentAt") LocalDateTime sentAt);

    /**
     * 카프카 발행 실패 시 재시도 횟수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OutboxEventEntity e SET e.retryCount = e.retryCount + 1 WHERE e.id = :id")
    void incrementRetryCount(@Param("id") String id);

    /**
     * 락을 거는 용도의 업데이트 쿼리 (발송 시도 횟수 증가)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OutboxEventEntity e SET e.retryCount = e.retryCount + 1 WHERE e.id = :id AND e.sent = false")
    int tryLockAndIncrement(@Param("id") String id);
}
