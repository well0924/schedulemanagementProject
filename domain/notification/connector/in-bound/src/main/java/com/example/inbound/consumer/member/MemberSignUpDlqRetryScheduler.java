package com.example.inbound.consumer.member;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.logging.MDC.KafkaMDCUtil;
import com.example.notification.model.FailMessageModel;
import com.example.notification.service.FailedMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@AllArgsConstructor
public class MemberSignUpDlqRetryScheduler {

    private final FailedMessageService failedService;
    private final KafkaTemplate<String, MemberSignUpKafkaEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final int MAX_RETRY_COUNT = 5;
    public static int EXECUTION_COUNT = 0;

    @Timed(value = "kafka.dlq.signup.retry.duration", description = "회원가입 DLQ 재처리 시간")
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    @SchedulerLock(name = "retryMemberSignUpDlq", lockAtMostFor = "PT10M", lockAtLeastFor = "PT2S")
    public void retryMemberSignUps() {
        EXECUTION_COUNT++;
        log.info("실행됨: " + EXECUTION_COUNT);
        List<FailMessageModel> list = failedService
                .findByResolvedFalse()
                .stream()
                .filter(e -> "MEMBER_SIGNUP".equals(e.getMessageType()))
                .toList();

        for (FailMessageModel entity : list) {

            if(entity.getRetryCount() >= MAX_RETRY_COUNT) {
                log.warn("재시도 초과 - id={}, payload={}", entity.getId(), entity.getPayload());
                entity.setDead();
                entity.setLastTriedAt();
                failedService.updateFailMessage(entity);
                continue;
            }

            try {
                MemberSignUpKafkaEvent event = objectMapper.readValue(entity.getPayload(), MemberSignUpKafkaEvent.class);
                KafkaMDCUtil.initMDC(event);
                String retryTopic = getRetryTopicByCountForMember(entity.getRetryCount());
                kafkaTemplate.send(retryTopic, event);
                entity.setResolved();
                log.info(" DLQ 재처리 성공 - member signup: id={}", entity.getId());
            } catch (Exception ex) {
                entity.setIncresementRetryCount();
                entity.setLastTriedAt();
                entity.setExceptionMessage(ex.getMessage());
                log.warn(" DLQ 재처리 실패 - member signup: id={}, reason={}", entity.getId(), ex.getMessage());
            } finally {
                KafkaMDCUtil.clear();
            }
            failedService.updateFailMessage(entity);
        }
    }

    private String getRetryTopicByCountForMember(int retryCount) {
        return switch (retryCount) {
            case 0 -> "member-signup.retry.5s";
            case 1 -> "member-signup.retry.10s";
            case 2 -> "member-signup.retry.30s";
            case 3 -> "member-signup.retry.60s";
            default -> "member-signup.retry.final";
        };
    }
}
