package com.example.notification.apimodel;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvents {
    private Long receiverId;
    private String message;
    private String notificationType;
    private LocalDateTime createdTime;
}
