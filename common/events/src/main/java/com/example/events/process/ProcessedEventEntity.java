package com.example.events.process;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "processed_event",
        indexes = {
                @Index(name = "idx_event_id", columnList = "eventId", unique = true)
        }
)
public class ProcessedEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String eventId; // Outbox PK or 비즈니스 키

    @Column(nullable = false)
    private LocalDateTime processedAt;

}
