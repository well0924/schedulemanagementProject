package com.example.rdbrepository;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "fail_email_entity")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailEmailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String toEmail;
    private String subject;
    @Column(columnDefinition = "TEXT")
    private String content;

    private boolean resolved;

    private LocalDateTime createdAt;

    public void markResolved() {
        this.resolved = true;
    }

}
