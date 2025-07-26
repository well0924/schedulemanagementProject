package com.example.rdb;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "failed_thumbnail", indexes = {
        @Index(name = "idx_resolved_retry", columnList = "resolved, retry_count")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedThumbnail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String storedFileName;

    private String reason;

    private int retryCount;

    private boolean resolved;

    private LocalDateTime lastTriedAt;

    public void markResolved() {
        this.resolved = true;
    }

    public void increaseRetry(String reason) {
        this.retryCount++;
        this.lastTriedAt = LocalDateTime.now();
        this.reason = reason;
    }
}
