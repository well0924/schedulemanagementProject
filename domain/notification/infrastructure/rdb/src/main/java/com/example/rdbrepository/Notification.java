package com.example.rdbrepository;

import com.example.jpa.config.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Table(
        name = "notification",
        indexes = {
                @Index(name = "idx_notification_user_id", columnList = "userId"),
                @Index(name = "idx_notification_scheduledAt_isSent", columnList = "scheduledAt, isSent"),
                @Index(name = "idx_notification_isRead", columnList = "isRead")
        }
)
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String message;
    private Boolean isRead; // 읽음 여부
    private Boolean isSent; // 발송여부
    private Long scheduleId;
    private Long userId;
    private String notificationType;
    private LocalDateTime scheduledAt; // 예약 발송 시간 (추후에 테이블을 분리할 필요가 있음)
}
