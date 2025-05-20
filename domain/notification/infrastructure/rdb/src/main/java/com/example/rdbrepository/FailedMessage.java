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

    @Lob
    private String payload;

    private int retryCount;

    private boolean resolved;

    private boolean dead; // retryCount 초과 시 마킹

    private String exceptionMessage;

    private LocalDateTime lastTriedAt;

    private LocalDateTime createdAt;


}
