package com.example.events.outbox;

import com.example.exception.dto.ErrorCode;
import com.example.exception.global.CustomExceptionHandler;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Entity
public class OutboxEventEntity {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    private String id;

    @Column(nullable = false)
    private String aggregateType; // 예: MEMBER, SCHEDULE

    @Column(nullable = false)
    private String aggregateId;   // 연관된 도메인 객체의 ID

    @Column(nullable = false)
    private String eventType;     // 예: MEMBER_REGISTERED, SCHEDULE_CREATED

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String payload;       // JSON 직렬화된 Kafka Event DTO

    @Column(nullable = false)
    private Boolean sent = false; // Kafka 발행 여부

    @Column(nullable = false)
    private Integer retryCount = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    // 발행 선점 정보. 한 번의 UPDATE로 여러 건을 선점할 때 이번 실행의 배치 ID를 찍는다.
    // claimedAt이 오래된 선점(발행 중 서버가 죽은 경우)은 다음 실행이 다시 가져간다.
    private String claimId;

    private LocalDateTime claimedAt;

    public void increaseRetryCount() {
        this.retryCount++;
    }

    public String resolveTopic() {
        return switch (this.aggregateType) {
            case "MEMBER" -> "member-signup-events";
            case "SCHEDULE" -> "notification-events";
            case "CHAT" -> "chat-history";
            default -> throw new CustomExceptionHandler(ErrorCode.INVALID_AGGREGATE_TYPE);
        };
    }
}
