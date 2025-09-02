package com.example.logging.MDC;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import org.slf4j.MDC;

import java.util.UUID;

public class KafkaMDCUtil {

    public static void initMDC(NotificationEvents event) {
        if (event.getReceiverId() != null)
            MDC.put("receiverId", String.valueOf(event.getReceiverId()));
        if (event.getNotificationType() != null)
            MDC.put("notificationType", event.getNotificationType().name());
        if (event.getEventId() != null)
            MDC.put("eventId", event.getEventId());
        MDC.put("requestId", UUID.randomUUID().toString());
    }

    public static void initMDC(MemberSignUpKafkaEvent event) {
        if (event.getReceiverId() != null)
            MDC.put("receiverId", String.valueOf(event.getReceiverId()));
        if (event.getEmail() != null)
            MDC.put("email", event.getEmail());
        if (event.getEventId() != null)
            MDC.put("eventId", event.getEventId());
        MDC.put("requestId", UUID.randomUUID().toString());
    }

    public static void clear() {
        MDC.clear();
    }
}
