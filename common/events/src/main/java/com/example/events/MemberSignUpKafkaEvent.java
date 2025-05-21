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
public class MemberSignUpKafkaEvent {

    private Long receiverId; // 알림 받는 사용자 ID
    private String username;
    private String email;
    private String message; // 예: "회원가입을 환영합니다!"
    private String notificationType; // SIGN_UP, PASSWORD_RESET 등
    private LocalDateTime createdTime;

    public static MemberSignUpKafkaEvent of(Long receiverId, String username, String email, String message, String type) {
        return MemberSignUpKafkaEvent
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
