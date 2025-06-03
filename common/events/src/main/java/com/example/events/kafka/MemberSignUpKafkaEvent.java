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

    public static com.example.events.MemberSignUpKafkaEvent of(Long receiverId, String username, String email, String message, String type) {
        return com.example.events.MemberSignUpKafkaEvent
                .builder()
                .receiverId(receiverId)
                .username(username)
                .email(email)
                .message(message)
                .notificationType(type)
                .createdTime(LocalDateTime.now())
                .build();
    }
}
