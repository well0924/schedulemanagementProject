package com.example.rdbrepository;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "failed_message", indexes = {
        // 스케줄러 쿼리 성능을 위한 복합 인덱스
        @Index(name = "idx_failed_message_retry", columnList = "resolved, dead, nextRetryTime")
})
public class FailedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 멱등성 체크를 위해 원본 이벤트의 ID를 별도 컬럼으로 저장
    private String eventId;

    private String topic;

    private String messageType;

    @Column(length = 2000)
    private String payload;

    private int retryCount;

    //처리 완료 여부 (true 시 스케줄러 대상 제외)
    private boolean resolved;

    private boolean dead; // retryCount 초과 시 마킹

    @Column(length = 2000)
    private String exceptionMessage;
    // 언제 메시지를 읽을지 결정하는 필드
    private LocalDateTime nextRetryTime;
    // 마지막 재시도 수행 시간
    private LocalDateTime lastTriedAt;
    // 최종 해결(성공) 시간
    private LocalDateTime resolvedAt;
    // 데이터 최초 생성 시간
    private LocalDateTime createdAt;

}
