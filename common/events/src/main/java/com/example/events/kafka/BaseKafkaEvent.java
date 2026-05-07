package com.example.events.kafka;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
public abstract class BaseKafkaEvent {
    @Builder.Default
    private String eventId = java.util.UUID.randomUUID().toString();
}
