package com.example.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvents {
    private Long receiverId;
    private String message;
    private String notificationType;
    private LocalDateTime createdTime;
}
