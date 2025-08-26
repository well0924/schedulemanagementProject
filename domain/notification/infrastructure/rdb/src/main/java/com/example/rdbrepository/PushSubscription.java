package com.example.rdbrepository;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId; // 유저 ID (FK)

    @Column(nullable = false, unique = true, length = 1000)
    private String endpoint;

    @Column(nullable = false, length = 255)
    private String p256dh;

    @Column(nullable = false, length = 255)
    private String auth;

    private Long expirationTime;  // nullable

    private String userAgent;

    private boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime revokedAt;

    public void deactivate() {
        this.active = false;
        this.revokedAt = LocalDateTime.now();
    }

}
