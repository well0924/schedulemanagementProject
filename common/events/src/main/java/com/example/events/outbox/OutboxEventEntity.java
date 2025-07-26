package com.example.events.outbox;

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

    public void markSent() {
        this.sent = true;
        this.sentAt = LocalDateTime.now();
    }

    public void increaseRetryCount() {
        this.retryCount++;
    }

    public String resolveTopic() {
        return switch (this.aggregateType) {
            case "MEMBER" -> "member-signup-events";
            case "SCHEDULE" -> "notification-events";
            default -> throw new IllegalArgumentException("알 수 없는 aggregateType: " + this.aggregateType);
        };
    }
}
