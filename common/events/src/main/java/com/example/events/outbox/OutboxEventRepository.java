package com.example.events.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity,String> {

    // 아직 Kafka로 전송되지 않은 이벤트를 가장 오래된 순서대로 100건 조회합니다.
    List<OutboxEventEntity> findTop100BySentFalseOrderByCreatedAtAsc();

    /**
     * 발행이 성공(sent=true)하고 특정 보관 기간(threshold)이 지난 데이터를 물리 삭제합니다.
     * @param threshold 삭제 기준 시간 (예: LocalDateTime.now().minusDays(3))
     * @return 삭제된 레코드 수
     */
    int deleteBySentTrueAndCreatedAtBefore(LocalDateTime threshold);
}
