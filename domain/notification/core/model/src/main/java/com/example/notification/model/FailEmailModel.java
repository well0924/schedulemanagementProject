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
public class FailEmailModel {

    private Long id;

    private String toEmail;

    private String subject;

    private String content;

    private boolean resolved;

    private LocalDateTime createdAt;


    public void markResolved(){
        this.resolved = true;
    }
}
