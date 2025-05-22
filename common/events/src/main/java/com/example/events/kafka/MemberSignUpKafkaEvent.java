package com.example.events.kafka;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberSignUpKafkaEvent {
    private Long receiverId;
    private String username;
    private String email;
    private String message;
    private String notificationType;
    private LocalDateTime createdTime;
}
