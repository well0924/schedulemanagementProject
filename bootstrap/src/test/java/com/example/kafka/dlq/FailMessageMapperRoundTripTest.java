package com.example.kafka.dlq;

import com.example.notification.mapper.NotificationEntityMapper;
import com.example.notification.mapper.NotificationMapper;
import com.example.notification.model.FailMessageModel;
import com.example.rdbrepository.FailedMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 실패 기록이 모델 → 엔티티 → 모델로 오갈 때 필드가 빠지지 않는지 확인
// messageType이 빠지면 스케줄러가 담당 행을 고르지 못하고(알림은 Set.contains(null)로 NPE, 회원가입은 항상 false),
// 수정 저장 때 컬럼이 null로 덮어써진다. dead가 빠지면 포기 처리가 저장되지 않는다.
class FailMessageMapperRoundTripTest {

    private final NotificationMapper toModel = new NotificationMapper();
    private final NotificationEntityMapper toEntity = new NotificationEntityMapper();

    @Test
    void 실패기록의_모든_상태_필드가_왕복_후에도_유지된다() {
        LocalDateTime now = LocalDateTime.now();
        FailMessageModel original = FailMessageModel.builder()
                .id(1L)
                .eventId("evt-1")
                .topic("notification-events")
                .messageType("NOTIFICATION")
                .payload("{}")
                .retryCount(3)
                .resolved(false)
                .dead(true)
                .exceptionMessage("boom")
                .nextRetryTime(now.plusMinutes(5))
                .lastTriedAt(now)
                .createdAt(now.minusMinutes(1))
                .build();

        FailedMessage entity = toEntity.toEntity(original);
        FailMessageModel restored = toModel.toModel(entity);

        assertThat(entity.getMessageType()).isEqualTo("NOTIFICATION");
        assertThat(entity.isDead()).isTrue();
        assertThat(restored).usingRecursiveComparison().isEqualTo(original);
    }
}
