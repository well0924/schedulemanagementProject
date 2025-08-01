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

    private String topic;

    private String messageType;

    private String payload;

    private int retryCount;

    private boolean resolved;

    private boolean dead;

    private String exceptionMessage;

    private LocalDateTime lastTriedAt;

    private LocalDateTime resolvedAt;

    private LocalDateTime createdAt;


    public void setResolved(){
        this.resolved = true;
    }

    public void setIncresementRetryCount(){
        this.retryCount++;;
    }

    public void setDead() {
        this.dead = true;
    }

    public void setLastTriedAt() {
        this.lastTriedAt = LocalDateTime.now();
    }

    public void setResolvedAt() {this.resolvedAt = LocalDateTime.now();}

    public void setExceptionMessage(String exceptionMessage){
        this.exceptionMessage = exceptionMessage;
    }

    public void setMessageType(String messageType) {this.messageType = messageType;}
}
