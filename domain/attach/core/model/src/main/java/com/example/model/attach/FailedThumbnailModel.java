package com.example.model.attach;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedThumbnailModel {
    private Long id;
    private String storedFileName;
    private String reason;
    private int retryCount;
    private boolean resolved;
    private LocalDateTime lastTriedAt;
}
