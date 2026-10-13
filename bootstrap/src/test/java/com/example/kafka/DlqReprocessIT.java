package com.example.kafka;

import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.events.kafka.NotificationEvents;
import com.example.events.process.ProcessedEventRepository;
import com.example.events.process.ProcessedEventService;
import com.example.inbound.consumer.schedule.NotificationDlqRetryScheduler;
import com.example.inbound.consumer.schedule.NotificationRetryTopicConsumer;
import com.example.inbound.consumer.slack.SlackNotifier;
import com.example.notification.service.FailedMessageService;
import com.example.notification.service.NotificationService;
import com.example.notification.service.WebPushService;
import com.example.rdbrepository.FailedMessage;
import com.example.rdbrepository.FailedMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

/**
 * 알림 DLQ 재처리 경로를 실제 Kafka·MySQL·Redis로 끝까지 돌려 본다.
 * 메인 컨슈머 실패(재시도 3회) → DLQ 토픽 → DLQ 컨슈머가 failed_message 저장 → 스케줄러가 retry 토픽으로 재발행
 * → 재시도 컨슈머가 메인 토픽으로 재전송 → (다시 실패하면) DLQ 컨슈머가 같은 행을 다시 연다.
 *
 * 단위 테스트는 목 모델을 써서 매퍼가 messageType·dead를 빠뜨린 문제를 놓쳤다. 여기서는 DB를 거친 행으로 확인한다.
 *
 * test 프로필에서는 스케줄러와 재시도 컨슈머가 @Profile("!test")로 빠지고, 기본 팩토리 리스너(DLQ·재시도)는
 * auto-startup=false라 시작되지 않는다. 그래서 재시도 컨슈머는 @Bean으로 등록해 직접 시작하고,
 * 스케줄러는 ShedLock·@Scheduled 없이 직접 만들어 원하는 시점에 한 번씩 호출한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class DlqReprocessIT {

    private static final String CONSUMER = "notification-group";
    private static final Set<String> DLQ_PATH_TOPICS = Set.of(
            "notification-events.DLQ",
            "notification-events.retry.5s", "notification-events.retry.10s",
            "notification-events.retry.30s", "notification-events.retry.60s",
            "notification-events.retry.final");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.0"));

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // 리스너가 붙기 전에 토픽이 없으면 첫 메시지를 놓칠 수 있어 미리 만든다
    @BeforeAll
    static void createTopics() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            List<NewTopic> topics = new java.util.ArrayList<>();
            topics.add(new NewTopic("notification-events", 1, (short) 1));
            DLQ_PATH_TOPICS.forEach(t -> topics.add(new NewTopic(t, 1, (short) 1)));
            admin.createTopics(topics).all().get(30, TimeUnit.SECONDS);
        }
    }

    @TestConfiguration
    static class RetryConsumerConfig {
        // 클래스의 @Profile("!test")는 @Bean 등록에는 적용되지 않는다
        @Bean
        NotificationRetryTopicConsumer notificationRetryTopicConsumer(
                @Qualifier("notificationKafkaTemplate") KafkaTemplate<String, NotificationEvents> kafkaTemplate,
                ObjectMapper objectMapper, SlackNotifier slackNotifier, MeterRegistry meterRegistry,
                FailedMessageService failedMessageService) {
            return new NotificationRetryTopicConsumer(kafkaTemplate, objectMapper, slackNotifier, meterRegistry, failedMessageService);
        }
    }

    @Autowired
    @Qualifier("notificationKafkaTemplate")
    KafkaTemplate<String, NotificationEvents> kafkaTemplate;
    @Autowired
    KafkaListenerEndpointRegistry registry;
    @Autowired
    FailedMessageService failedMessageService;
    @Autowired
    FailedMessageRepository failedMessageRepository;
    @Autowired
    NotificationService notificationService;
    @Autowired
    ProcessedEventRepository processedEventRepository;
    @Autowired
    WebPushService webPushService;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @SpyBean
    ProcessedEventService processedEventService;
    @MockBean
    SlackNotifier slackNotifier;

    NotificationDlqRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        registry.getListenerContainers().stream()
                .filter(c -> Arrays.stream(c.getContainerProperties().getTopics()).anyMatch(DLQ_PATH_TOPICS::contains))
                .filter(c -> !c.isRunning())
                .forEach(c -> c.start());
        scheduler = new NotificationDlqRetryScheduler(failedMessageService, kafkaTemplate, webPushService, objectMapper);
    }

    @Test
    @DisplayName("재발행한 메시지가 다시 실패하면 같은 행이 다시 열리고 retryCount가 오른다")
    void republishedMessageFailsAgain_rowIsReopened() {
        NotificationEvents event = event(80001L);
        failAlways(event);

        kafkaTemplate.send("notification-events", event.partitionKey(), event);

        // 1) 첫 실패: DLQ 컨슈머가 재처리 예약 시각과 함께 저장
        FailedMessage first = awaitRow(event, row -> !row.isResolved());
        assertThat(first.getMessageType()).isEqualTo("NOTIFICATION");
        assertThat(first.getRetryCount()).isZero();
        assertThat(first.getNextRetryTime()).isNotNull();

        // 2) 예약 시각이 지나면 스케줄러 조회에 잡힌다 (예전에는 nextRetryTime=null이라 영원히 안 잡혔다)
        awaitReadyToRetry(event);
        scheduler.retryNotifications();
        FailedMessage republished = row(event);
        assertThat(republished.isResolved()).isTrue();
        assertThat(republished.getMessageType()).isEqualTo("NOTIFICATION");

        // 3) retry.5s → 메인 토픽 → 다시 3회 실패 → DLQ → 같은 행을 다시 연다 (예전에는 isAlreadyRecorded로 버려졌다)
        FailedMessage reopened = awaitRow(event, row -> !row.isResolved() && row.getRetryCount() == 1);
        assertThat(reopened.getId()).isEqualTo(first.getId());
        assertThat(reopened.getMessageType()).isEqualTo("NOTIFICATION");
        assertThat(reopened.getNextRetryTime()).isAfter(LocalDateTime.now());
        assertThat(failedMessageRepository.findAll().stream()
                .filter(r -> event.getEventId().equals(r.getEventId()))).hasSize(1);
    }

    @Test
    @DisplayName("재발행 뒤 정상 처리되면 알림이 1건 저장되고 실패 행은 해결 상태로 남는다")
    void republishedMessageSucceeds_notificationSavedOnce() {
        NotificationEvents event = event(80002L);
        failAlways(event);

        kafkaTemplate.send("notification-events", event.partitionKey(), event);
        awaitRow(event, row -> !row.isResolved());
        awaitReadyToRetry(event);

        // 장애가 풀린 상황
        doCallRealMethod().when(processedEventService).saveProcessedEvent(eq(CONSUMER), eq(event.getEventId()));
        scheduler.retryNotifications();

        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> assertThat(
                processedEventRepository.existsByConsumerAndEventId(CONSUMER, event.getEventId())).isTrue());
        assertThat(notificationService.getNotificationsByUserId(event.getReceiverId())).hasSize(1);
        FailedMessage row = row(event);
        assertThat(row.isResolved()).isTrue();
        assertThat(row.isDead()).isFalse();
    }

    @Test
    @DisplayName("재시도 횟수가 다 차면 retry.final로 보내고 dead로 저장된다")
    void retriesExhausted_markedDead() {
        NotificationEvents event = event(80003L);
        failAlways(event);

        kafkaTemplate.send("notification-events", event.partitionKey(), event);
        awaitRow(event, row -> !row.isResolved());
        // 네 번 재발행하고 모두 실패해 돌아온 상태로 만든다 (DB 시각과 JVM 시각대가 달라 NOW() 대신 파라미터로 넣는다)
        jdbcTemplate.update("UPDATE failed_message SET retry_count = 4, next_retry_time = ? WHERE event_id = ?",
                LocalDateTime.now().minusSeconds(1), event.getEventId());

        scheduler.retryNotifications();

        // 스케줄러가 저장한 dead와 retry.final 컨슈머의 markAsDeadByEventId가 모두 DB에 남아야 한다
        // (예전에는 매퍼가 dead를 빠뜨려 저장되지 않았다)
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            FailedMessage row = row(event);
            assertThat(row.isDead()).isTrue();
            assertThat(row.isResolved()).isFalse();
            assertThat(row.getMessageType()).isEqualTo("NOTIFICATION");
        });
        org.mockito.Mockito.verify(slackNotifier, org.mockito.Mockito.timeout(30_000))
                .send(eq("Dlq_Notification"), org.mockito.ArgumentMatchers.anyString());
    }

    private void failAlways(NotificationEvents event) {
        doThrow(new RuntimeException("강제 실패: 처리 기록 저장"))
                .when(processedEventService).saveProcessedEvent(eq(CONSUMER), eq(event.getEventId()));
    }

    private FailedMessage awaitRow(NotificationEvents event, java.util.function.Predicate<FailedMessage> condition) {
        await().atMost(60, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).until(() ->
                failedMessageRepository.findFirstByEventIdAndMessageTypeOrderByIdDesc(event.getEventId(), "NOTIFICATION")
                        .filter(condition).isPresent());
        return row(event);
    }

    private void awaitReadyToRetry(NotificationEvents event) {
        await().atMost(20, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).until(() ->
                failedMessageService.findReadyToRetry().stream()
                        .anyMatch(m -> event.getEventId().equals(m.getEventId())));
    }

    private FailedMessage row(NotificationEvents event) {
        return failedMessageRepository.findFirstByEventIdAndMessageTypeOrderByIdDesc(event.getEventId(), "NOTIFICATION")
                .orElseThrow();
    }

    private NotificationEvents event(long receiverId) {
        return NotificationEvents.builder()
                .receiverId(receiverId)
                .scheduleId(1L)
                .message("DLQ 재처리 검증 알림")
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .notificationChannel(NotificationChannel.WEB)
                .createdTime(LocalDateTime.now())
                .build();
    }
}
