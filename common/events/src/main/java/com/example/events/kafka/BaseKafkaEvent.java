package com.example.events.kafka;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseKafkaEvent {
    private String eventId;
}
