package com.example.kafka;

import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.events.kafka.NotificationEvents;
import com.example.events.process.ProcessedEventRepository;
import com.example.events.process.ProcessedEventService;
import com.example.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 실제 NotificationEventConsumer를 실제 Kafka·MySQL·Redis로 돌려 실패 처리를 검증한다.
 * (단위 테스트는 목이라 @Transactional 롤백과 DefaultErrorHandler → DLQ를 확인하지 못한다)
 *
 * 실패는 처리 기록 저장(processed_event)에서 일으킨다. 알림 행은 그보다 먼저 저장되므로,
 * 실패 뒤 알림 행이 남아 있지 않으면 두 저장이 한 트랜잭션으로 롤백된 것이다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class NotificationConsumerFailureIT {

    private static final String CONSUMER = "notification-group";

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

    @Autowired
    @Qualifier("notificationKafkaTemplate")
    KafkaTemplate<String, NotificationEvents> kafkaTemplate;

    @Autowired
    NotificationService notificationService;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @SpyBean
    ProcessedEventService processedEventService;

    @SpyBean
    SimpMessagingTemplate simpMessagingTemplate;

    @Test
    @DisplayName("저장이 계속 실패하면 재시도 3회 뒤 DLQ로 가고, 알림 행·처리 기록은 롤백되어 남지 않는다")
    void persistentFailure_goesToDlq_andRollsBack() {
        long receiverId = 70001L;
        NotificationEvents event = event(receiverId);
        doThrow(new RuntimeException("강제 실패: 처리 기록 저장"))
                .when(processedEventService).saveProcessedEvent(eq(CONSUMER), eq(event.getEventId()));

        kafkaTemplate.send("notification-events", event);

        // H1: DLQ 도착
        await().atMost(60, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(dlqValues()).anyMatch(v -> v.contains(event.getEventId())));

        // H1: 최초 1회 + 재시도 3회
        verify(processedEventService, times(4)).saveProcessedEvent(CONSUMER, event.getEventId());

        // H2: 먼저 저장한 알림 행까지 롤백
        assertThat(notificationService.getNotificationsByUserId(receiverId)).isEmpty();
        assertThat(processedEventRepository.existsByConsumerAndEventId(CONSUMER, event.getEventId())).isFalse();
    }

    @Test
    @DisplayName("한 번 실패한 뒤 재시도에서 정상 처리되고, 알림 행은 정확히 1건 남는다")
    void transientFailure_recoversOnRetry_exactlyOnce() {
        long receiverId = 70002L;
        NotificationEvents event = event(receiverId);
        doThrow(new RuntimeException("일시 장애"))
                .doCallRealMethod()
                .when(processedEventService).saveProcessedEvent(eq(CONSUMER), eq(event.getEventId()));

        kafkaTemplate.send("notification-events", event);

        // H3: 재시도에서 처리 완료
        await().atMost(60, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(
                        processedEventRepository.existsByConsumerAndEventId(CONSUMER, event.getEventId())).isTrue());

        // H2: 실패한 첫 시도의 알림 행은 롤백되어, 최종적으로 1건
        assertThat(notificationService.getNotificationsByUserId(receiverId)).hasSize(1);
        verify(processedEventService, times(2)).saveProcessedEvent(CONSUMER, event.getEventId());
        assertThat(dlqValues()).noneMatch(v -> v.contains(event.getEventId()));
    }

    @Test
    @DisplayName("실시간 전달은 저장 트랜잭션이 끝난 뒤(트랜잭션·동기화 없음)에 실행된다")
    void delivery_runsOutsideTransaction() {
        long receiverId = 70003L;
        NotificationEvents event = event(receiverId);
        AtomicReference<Boolean> txActiveAtDelivery = new AtomicReference<>();
        AtomicReference<Boolean> syncActiveAtDelivery = new AtomicReference<>();
        doAnswer(invocation -> {
            txActiveAtDelivery.set(TransactionSynchronizationManager.isActualTransactionActive());
            syncActiveAtDelivery.set(TransactionSynchronizationManager.isSynchronizationActive());
            return invocation.callRealMethod();
        }).when(simpMessagingTemplate).convertAndSend(eq("/topic/notifications/" + receiverId), any(Object.class));

        kafkaTemplate.send("notification-events", event);

        await().atMost(60, TimeUnit.SECONDS).until(() -> txActiveAtDelivery.get() != null);

        // H4: 전달 시점엔 트랜잭션이 끝나 커넥션이 반납된 상태
        assertThat(txActiveAtDelivery.get()).isFalse();
        assertThat(syncActiveAtDelivery.get()).isFalse();
        // 전달 전에 저장은 커밋돼 있어야 한다
        assertThat(processedEventRepository.existsByConsumerAndEventId(CONSUMER, event.getEventId())).isTrue();
        assertThat(notificationService.getNotificationsByUserId(receiverId)).hasSize(1);
    }

    private NotificationEvents event(long receiverId) {
        return NotificationEvents.builder()
                .receiverId(receiverId)
                .scheduleId(1L)
                .message("실패 처리 검증 알림")
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .notificationChannel(NotificationChannel.WEB)
                .createdTime(LocalDateTime.now())
                .build();
    }

    private List<String> dlqValues() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-dlq-reader-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("notification-events.DLQ"));
            List<String> values = new java.util.ArrayList<>();
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(3))) {
                values.add(record.value());
            }
            return values;
        }
    }
}
