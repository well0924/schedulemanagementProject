package com.example.rdbrepository;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Builder.Default
    private boolean scheduleCreatedEnabled = true;

    @Builder.Default
    private boolean scheduleUpdatedEnabled = true;

    @Builder.Default
    private boolean scheduleDeletedEnabled = true;

    @Builder.Default
    private boolean scheduleRemindEnabled = true;

    @Builder.Default
    private boolean webEnabled = true;

    @Builder.Default
    private boolean emailEnabled = false;

    @Builder.Default
    private boolean pushEnabled = false;
}
