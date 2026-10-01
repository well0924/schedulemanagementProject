package com.example.events.process;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
// 같은 이벤트를 여러 컨슈머 그룹이 각자 처리하므로(예: chat-history-save, pattern-analysis)
// 중복 판단은 (consumer, eventId) 단위로 한다. eventId만으로 막으면 먼저 처리한 그룹이 나머지 그룹을 건너뛰게 만든다.
@Table(
        name = "processed_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_processed_event_consumer_event", columnNames = {"consumer", "eventId"})
        }
)
public class ProcessedEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String consumer; // 처리한 컨슈머 그룹 (groupId)

    @Column(nullable = false, length = 100)
    private String eventId; // Outbox PK or 비즈니스 키

    @Column(nullable = false)
    private LocalDateTime processedAt;

}
