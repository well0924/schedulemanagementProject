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

    // 미발행(sent=false) 이벤트 백로그 크기 - poller 드레인 속도 모니터링용
    long countBySentFalse();

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

    // ---- 배치 선점 방식 (2026-10-02) ----

    /**
     * 미발행 이벤트를 생성순으로 최대 limit건 선점한다. 한 번의 UPDATE라 DB 왕복은 1번이다.
     * claim_id가 비어 있거나 선점한 지 오래된(staleBefore 이전) 행만 가져가므로,
     * 같은 행을 두 실행이 동시에 선점하지 않는다. 시도 횟수(retry_count)도 함께 올린다.
     * (MySQL 단일 테이블 UPDATE는 ORDER BY ... LIMIT를 지원한다)
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE outbox_event_entity " +
            "SET claim_id = :claimId, claimed_at = :now, retry_count = retry_count + 1 " +
            "WHERE sent = false AND (claim_id IS NULL OR claimed_at < :staleBefore) " +
            "ORDER BY created_at " +
            "LIMIT :limit", nativeQuery = true)
    int claimBatch(@Param("claimId") String claimId,
                   @Param("now") LocalDateTime now,
                   @Param("staleBefore") LocalDateTime staleBefore,
                   @Param("limit") int limit);

    // 이번 실행이 선점한 행 (생성순)
    List<OutboxEventEntity> findByClaimIdOrderByCreatedAtAsc(String claimId);

    // 발행 성공 일괄 반영
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OutboxEventEntity e SET e.sent = true, e.sentAt = :sentAt, e.claimId = null WHERE e.id IN :ids")
    int markSentByIds(@Param("ids") List<String> ids, @Param("sentAt") LocalDateTime sentAt);

    // 발행 실패한 행의 선점 해제 → 다음 실행에서 다시 선점된다
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OutboxEventEntity e SET e.claimId = null, e.claimedAt = null WHERE e.id IN :ids AND e.sent = false")
    int releaseClaims(@Param("ids") List<String> ids);
}
