package com.example.events.kafka;

import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

@Getter
@SuperBuilder
@Jacksonized // Jackson이 SuperBuilder로 역직렬화 (기본 생성자 없음)
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

    /** Kafka 메시지 키. Outbox 발행 키(aggregateId = 회원 ID)와 같아야 재처리 때도 같은 파티션으로 간다. */
    public String partitionKey() {
        return receiverId == null ? null : String.valueOf(receiverId);
    }
}
