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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@AllArgsConstructor
public class MemberSignUpDlqRetryScheduler {

    private final FailedMessageService failedService;
    @Qualifier("memberKafkaTemplate")
    private final KafkaTemplate<String, MemberSignUpKafkaEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final int MAX_RETRY_COUNT = 5;
    public static int EXECUTION_COUNT = 0;

    @Timed(value = "kafka.dlq.signup.retry.duration", description = "회원가입 DLQ 재처리 시간")
    @Scheduled(fixedDelay = 30 * 1000) //1.스케줄러의 시간을 30초로 변경. -> 재시도 토픽과 싱크를 맞추기 위해서
    @SchedulerLock(name = "retryMemberSignUpDlq", lockAtMostFor = "PT10M", lockAtLeastFor = "PT2S")
    public void retryMemberSignUps() {
        EXECUTION_COUNT++;
        log.info("실행됨: " + EXECUTION_COUNT);

        // 시간이 다 된것만 교체하기
        List<FailMessageModel> list = failedService
                .findReadyToRetry()
                .stream()
                .filter(e -> "MEMBER_SIGNUP".equals(e.getMessageType()))
                .toList();

        for (FailMessageModel entity : list) {

            // MAX_RETRY_COUNT 체크는 이미 모델의 resolveFailure에서 처리하도록 설계했으니 여기선 로직만 집중
            if(entity.isDead()) continue;

            try {
                MemberSignUpKafkaEvent event = objectMapper.readValue(entity.getPayload(), MemberSignUpKafkaEvent.class);
                KafkaMDCUtil.initMDC(event);
                // 지연 토픽 발행
                String retryTopic = getRetryTopicByCountForMember(entity.getRetryCount());
                kafkaTemplate.send(retryTopic, event);
                // 마킹 성공
                entity.resolveSuccess(event.getNotificationType());
                log.info(" DLQ 재처리 성공 - member signup: id={}", entity.getId());
            } catch (Exception ex) {
                entity.resolveFailure(ex);
                log.warn(" DLQ 재처리 실패 - member signup: id={}, reason={}", entity.getId(), ex.getMessage());
            } finally {
                KafkaMDCUtil.clear();
            }
            // 최종 상태 반영
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
