package com.example.events.spring;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AttachCreatedEvent {
    private Long attachId;
}
