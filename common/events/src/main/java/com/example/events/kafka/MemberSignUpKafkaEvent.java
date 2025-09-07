package com.example.events.kafka;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberSignUpKafkaEvent extends BaseKafkaEvent {
    private Long receiverId;
    private String username;
    private String email;
    private String message;
    private String notificationType;// SIGN_UP
    private LocalDateTime createdTime;

    public static MemberSignUpKafkaEvent of(Long receiverId, String username, String email) {
        return MemberSignUpKafkaEvent
                .builder()
                .receiverId(receiverId)
                .username(username)
                .email(email)
                .message("🎉 환영합니다, " + username + "님! 회원가입이 완료되었습니다.")
                .notificationType("SIGN_UP")
                .createdTime(LocalDateTime.now())
                .build();
    }
}
