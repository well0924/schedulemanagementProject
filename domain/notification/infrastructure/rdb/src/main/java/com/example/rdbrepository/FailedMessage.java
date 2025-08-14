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
public class FailedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String topic;

    private String messageType;

    @Column(unique = true, length = 2000)
    private String payload;

    private int retryCount;

    private boolean resolved;

    private boolean dead; // retryCount 초과 시 마킹

    @Column(length = 2000)
    private String exceptionMessage;

    private LocalDateTime lastTriedAt;

    private LocalDateTime resolvedAt;

    private LocalDateTime createdAt;

}
