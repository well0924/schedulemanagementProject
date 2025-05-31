package com.example.kafka.dlq;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import com.example.notification.model.FailMessageModel;
import com.example.notification.service.FailedMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Profile("test")
@Component
@AllArgsConstructor
public class DlqTestConsumer {

    private final List<MemberSignUpKafkaEvent> MemberDlqMessages = new ArrayList<>();

    private final List<NotificationEvents> NotificationDlqMessage = new ArrayList<>();

    private final FailedMessageService failedMessageService;

    private final ObjectMapper objectMapper; // 직렬화용


    @KafkaListener(topics = "member-signup-events.DLQ",
            groupId ="test-member-signup-group",
            containerFactory = "memberKafkaListenerFactory")
    public void consumeDlq(MemberSignUpKafkaEvent event) {

        MemberDlqMessages.add(event);

        try {
            // JSON 직렬화
            String payload = objectMapper.writeValueAsString(event);

            // 실패 이력 저장
            failedMessageService.createFailMessage(FailMessageModel.builder()
                    .topic("member-signup-events")
                    .messageType("MemberSignUpKafkaEvent")
                    .exceptionMessage("테스트 실패 시뮬레이션")
                    .payload(payload)
                    .resolved(false)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("DLQ 테스트용 직렬화 실패", e);
        }
    }

    @KafkaListener(topics = "notification-events.DLQ",
            groupId = "test-notification-group",
            containerFactory = "notificationKafkaListenerFactory")
    public void consumeDlq(NotificationEvents event) {

        NotificationDlqMessage.add(event);

        try {
            // JSON 직렬화
            String payload = objectMapper.writeValueAsString(event);

            // 실패 이력 저장
            failedMessageService.createFailMessage(FailMessageModel.builder()
                    .topic("notification-events")
                    .messageType("NotificationEvents")
                    .exceptionMessage("테스트 실패 시뮬레이션")
                    .payload(payload)
                    .resolved(false)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("DLQ 테스트용 직렬화 실패", e);
        }
    }

    public List<MemberSignUpKafkaEvent> getMemberDlqMessages() {
        return MemberDlqMessages;
    }

    public List<NotificationEvents> getNotificationDlqMessages() {
        return NotificationDlqMessage;
    }

    public void clear() {
        MemberDlqMessages.clear();
        NotificationDlqMessage.clear();
    }
}
