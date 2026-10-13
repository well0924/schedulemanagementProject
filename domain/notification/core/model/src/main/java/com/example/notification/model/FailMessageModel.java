package com.example.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailMessageModel {

    private Long id;

    private String eventId;

    private String topic;

    private String messageType;

    private String payload;

    private int retryCount;

    private boolean resolved;

    private boolean dead;

    private String exceptionMessage;
    // 언제 메시지를 읽을지 결정하는 필드
    private LocalDateTime nextRetryTime;

    private LocalDateTime lastTriedAt;

    private LocalDateTime resolvedAt;

    private LocalDateTime createdAt;

    // messageType은 바꾸지 않는다. 스케줄러가 messageType(NOTIFICATION, MEMBER_SIGNUP 등)으로 담당 행을 고르기 때문에,
    // 여기서 알림 유형(SCHEDULE_CREATED 등)으로 덮어쓰면 다시 열린 행을 어느 스케줄러도 집지 않는다.
    public void resolveSuccess() {
        this.resolved = true;
        this.lastTriedAt = LocalDateTime.now();
        this.resolvedAt = LocalDateTime.now();
        this.nextRetryTime = null; // 성공을 했으니 다음시간은 지우기.
    }

    public void resolveFailure(Exception ex) {
        this.retryCount++;
        this.lastTriedAt = LocalDateTime.now();
        this.exceptionMessage = ex.getMessage();
        this.nextRetryTime = nextRetryTimeByCount();
    }

    // 재발행한 메시지가 메인 컨슈머에서 다시 실패해 DLQ로 돌아온 경우.
    // 스케줄러는 재발행 직후 resolved=true로 바꾸므로, 같은 행을 다시 미해결로 돌리고 다음 재시도 단계로 넘긴다.
    public void reopen(String reason) {
        this.resolved = false;
        this.resolvedAt = null;
        this.retryCount++;
        this.lastTriedAt = LocalDateTime.now();
        this.exceptionMessage = reason;
        this.nextRetryTime = nextRetryTimeByCount();
    }

    // retry Topic 으로 보내기 위해서 다음 실행 시간을 지수적으로 증가
    private LocalDateTime nextRetryTimeByCount() {
        return switch (this.retryCount) {
            case 1 -> LocalDateTime.now().plusSeconds(30);
            case 2 -> LocalDateTime.now().plusMinutes(1);
            case 3 -> LocalDateTime.now().plusMinutes(5);
            case 4 -> LocalDateTime.now().plusMinutes(10);
            default -> LocalDateTime.now().plusMinutes(30);
        };
    }

    public void markAsDead() {
        this.resolved = false;
        this.dead = true;
        this.lastTriedAt = LocalDateTime.now();
        this.nextRetryTime = null;
    }
}
