package com.example.rdbrepository;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String userMessage;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String assistantResponse;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
