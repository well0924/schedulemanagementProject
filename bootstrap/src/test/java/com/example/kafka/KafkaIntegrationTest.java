package com.example.kafka;

import com.example.enumerate.member.Roles;
import com.example.events.enums.AggregateType;
import com.example.events.enums.EventType;
import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import com.example.events.outbox.OutboxEventPublisher;
import com.example.events.outbox.OutboxEventRepository;
import com.example.events.outbox.OutboxEventService;
import com.example.events.process.ProcessedEventRepository;
import com.example.events.process.ProcessedEventService;
import com.example.inbound.consumer.slack.SlackNotifier;
import com.example.kafka.dlq.DlqTestConsumer;
import com.example.kafka.dlq.MemberSignUpDlqRetryTestScheduler;
import com.example.kafka.dlq.NotificationDlqRetryTestScheduler;
import com.example.model.member.MemberModel;
import com.example.notification.model.NotificationSettingModel;
import com.example.notification.service.FailedMessageService;
import com.example.notification.service.NotificationService;
import com.example.notification.service.NotificationSettingService;
import com.example.notification.NotificationType;
import com.example.notification.model.NotificationModel;
import com.example.notification.service.ReminderNotificationService;
import com.example.outbound.notification.NotificationOutConnector;
import com.example.service.member.MemberService;
import com.example.service.schedule.ScheduleDomainService;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.backoff.FixedBackOff;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Import(KafkaIntegrationTest.KafkaTestConfig.class)
@ContextConfiguration(classes = {KafkaIntegrationTest.KafkaTestConfig.class})
@Testcontainers
public class KafkaIntegrationTest {

    @Container
    static KafkaContainer kafka1 = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.0"))
            .withNetwork(Network.newNetwork());

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        String bootstrapServers = kafka1.getBootstrapServers();
        registry.add("spring.kafka.bootstrap-servers", () -> bootstrapServers);
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Qualifier("testMemberKafkaTemplate")
    @Autowired
    KafkaTemplate<String, MemberSignUpKafkaEvent> kafkaTemplate;

    @Qualifier("testNotificationKafkaTemplate")
    @Autowired
    KafkaTemplate<String, NotificationEvents> scheduleTemplate;

    @Autowired
    EntityManager em;

    @Autowired
    OutboxEventService outboxEventService;

    @Autowired
    OutboxEventPublisher outboxEventPublisher;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Autowired
    MemberService memberService;

    @Autowired
    NotificationOutConnector notificationOutConnector;

    @Autowired
    NotificationService notificationService;

    @Autowired
    NotificationSettingService notificationSettingService;

    @Autowired
    ReminderNotificationService reminderNotificationService;

    @Autowired
    ScheduleDomainService scheduleDomainService;

    @Autowired
    DlqTestConsumer dlqTestConsumer;

    @SpyBean
    NotificationDlqRetryTestScheduler notificationDlqRetryScheduler;

    @SpyBean
    MemberSignUpDlqRetryTestScheduler retryScheduler;

    @Autowired
    FailedMessageService failedMessageService;

    @Autowired
    ProcessedEventService processedEventService;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Autowired
    SlackNotifier slackNotifier;

    @TestConfiguration
    static class KafkaTestConfig {

        @Bean
        @Primary
        public ProducerFactory<String, MemberSignUpKafkaEvent> memberProducerFactory() {
            Map<String, Object> config = new HashMap<>();
            config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka1.getBootstrapServers());
            config.put(ProducerConfig.ACKS_CONFIG, "all"); // 모든 복제본 저장 확인
            config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 프로듀서 자체 멱등성 활성화
            config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
            config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
            return new DefaultKafkaProducerFactory<>(config);
        }

        @Bean(name = "testMemberKafkaTemplate")
        @Primary
        public KafkaTemplate<String, MemberSignUpKafkaEvent> memberKafkaTemplate(
                ProducerFactory<String, MemberSignUpKafkaEvent> pf) {
            return new KafkaTemplate<>(pf);
        }

        @Bean
        public DefaultErrorHandler errorHandler(@Qualifier("testMemberKafkaTemplate") KafkaTemplate<String,MemberSignUpKafkaEvent> template) {
            var recovered = new DeadLetterPublishingRecoverer(template,
                    (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition()));
            var backoff = new FixedBackOff(1000L, 1); // 즉시 재시도 1회
            return new DefaultErrorHandler(recovered, backoff);
        }

        @Bean(name = "testMemberKafkaListenerFactory")
        @Primary
        public ConcurrentKafkaListenerContainerFactory<String, MemberSignUpKafkaEvent> memberKafkaListenerFactory() {
            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka1.getBootstrapServers());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-member-group");
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
            props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
            props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, MemberSignUpKafkaEvent.class.getName());

            ConsumerFactory<String, MemberSignUpKafkaEvent> cf =
                    new DefaultKafkaConsumerFactory<>(props,
                            new StringDeserializer(),
                            new JsonDeserializer<>(MemberSignUpKafkaEvent.class, false));

            ConcurrentKafkaListenerContainerFactory<String, MemberSignUpKafkaEvent> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(cf);
            factory.setCommonErrorHandler(errorHandler(memberKafkaTemplate(memberProducerFactory())));
            return factory;
        }

        ////// 일정 관련

        @Bean
        public ProducerFactory<String, NotificationEvents> notificationEventsProducerFactory() {
            Map<String, Object> config = new HashMap<>();
            config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka1.getBootstrapServers());
            config.put(ProducerConfig.ACKS_CONFIG, "all"); // 모든 복제본 저장 확인
            config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 프로듀서 자체 멱등성 활성화
            config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
            config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
            return new DefaultKafkaProducerFactory<>(config);
        }

        @Bean(name = "testNotificationKafkaTemplate")
        public KafkaTemplate<String,NotificationEvents> notificationKafkaTemplate(
                @Qualifier("notificationEventsProducerFactory")
                ProducerFactory<String,NotificationEvents> pf) {
            return new KafkaTemplate<>(pf);
        }

        @Bean
        public DefaultErrorHandler notificationErrorHandler(@Qualifier("testNotificationKafkaTemplate") KafkaTemplate<String, NotificationEvents> template) {
            var recover = new DeadLetterPublishingRecoverer(template,
                    (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition()));
            var backoff = new FixedBackOff(1000L, 1);
            return new DefaultErrorHandler(recover, backoff);
        }

        @Bean(name = "testNotificationKafkaListenerFactory")
        public ConcurrentKafkaListenerContainerFactory<String, NotificationEvents> notificationKafkaListenerFactory() {
            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka1.getBootstrapServers());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-notification-group");
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
            props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
            props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, NotificationEvents.class.getName());

            ConsumerFactory<String, NotificationEvents> cf =
                    new DefaultKafkaConsumerFactory<>(props,
                            new StringDeserializer(),
                            new JsonDeserializer<>(NotificationEvents.class, false));

            ConcurrentKafkaListenerContainerFactory<String, NotificationEvents> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(cf);
            factory.setCommonErrorHandler(notificationErrorHandler(notificationKafkaTemplate(notificationEventsProducerFactory())));
            return factory;
        }

        // outbox 전용.
        @Bean(name = "testObjectKafkaTemplate")
        public KafkaTemplate<String, Object> objectKafkaTemplate() {
            Map<String, Object> config = new HashMap<>();
            config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka1.getBootstrapServers());
            config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
            config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true); // Outbox 이벤트 직렬화 위해 필요
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
        }
    }

    @BeforeAll
    static void createTopics() throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafka1.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(props)) {
            List<NewTopic> topics = Arrays.asList(
                    new NewTopic("member-signup-events", 1, (short) 1),
                    new NewTopic("member-signup-events.DLQ", 1, (short) 1),
                    new NewTopic("notification-events",1,(short) 1),
                    new NewTopic("notification-events.DLQ",1,(short) 1)
            );
            admin.createTopics(topics).all().get();
        }
    }

    @Test
    @Disabled
    @DisplayName("회원가입후 정상적으로 카프카에 송신이 되고 알림내역이 정상적으로 저장이 되는가?")
    void memberSignUpNotificationSuccessTest(){
        // 1. 회원 생성 (이벤트 포함)
        MemberModel member = MemberModel.builder()
                .userId("testUser")
                .userEmail("test@test.com")
                .userPhone("010-0000-0000")
                .userName("tester")
                .password("12345")
                .roles(Roles.ROLE_USER)
                .build();

        MemberModel saved = memberService.createMember(member);

        //회원가입 이벤트
        MemberSignUpKafkaEvent event = MemberSignUpKafkaEvent.builder()
                .receiverId(123L)
                .username("testuser")
                .email("test@test.com")
                .build();

        //2.카프카로 전송
        kafkaTemplate.send("member-signup-events", event);

        //3.Awaitility로 알림 저장 완료 기다리기
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var notiList = notificationService.getNotificationsByUserId(saved.getId());
                    assertThat(notiList).isNotEmpty();
                    assertThat(notiList.get(0).getMessage()).contains("환영합니다");
                });
    }

    @Test
    @DisplayName("회원 컨슈머에서 실패를 했을경우에 DLQ로 진행이 되는 경우")
    public void MemberSignUpDLQTest1() {
        dlqTestConsumer.clear();

        MemberSignUpKafkaEvent event = MemberSignUpKafkaEvent.builder()
                .receiverId(999L)
                .message("강제실패 알림")
                .username("dlquser")
                .email("fail@test.com")
                .build();

        kafkaTemplate.send("member-signup-events", event);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<MemberSignUpKafkaEvent> messages = dlqTestConsumer.getMemberDlqMessages();
                    assertThat(messages).isNotEmpty();
                    assertThat(messages.get(0).getEmail()).isEqualTo("fail@test.com");
                });
    }


    @Test
    @DisplayName("회원 DLQ로 보내진 후 실패이력을 저장을 하고 재처리 하기")
    public void MemberSignUpDLQTest2(){
        // 1. DLQ 유도 (Consumer에서 실패할 email 설정)
        MemberSignUpKafkaEvent event = MemberSignUpKafkaEvent.builder()
                .receiverId(999L)
                .message("강제실패 알림")
                .username("retryUser")
                .email("fail@test.com")
                .build();

        kafkaTemplate.send("member-signup-events", event);

        // 2. Await DLQ 저장
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var failList = failedMessageService.findByResolvedFalse();
                    assertThat(failList).isNotEmpty();
                });

        // 3. 수동으로 재처리 (스케줄러 직접 호출)
        retryScheduler.retryMemberSignUps();

        //스파이를 통해 호출 여부 검증
        verify(retryScheduler, times(1)).retryMemberSignUps();

        // 4. RetryTopicConsumer → 원래 토픽으로 재전송됐는지 확인
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<MemberSignUpKafkaEvent> messages = dlqTestConsumer.getMemberDlqMessages();
                    assertThat(messages).isNotEmpty();
                    assertThat(messages.get(0).getEmail()).isEqualTo("fail@test.com");
                });
    }

    @Test
    @Disabled
    @DisplayName("일정 생성시(첨부파일 없는 경우) 정상적으로 알림이 작동이 되는가?")
    public void ScheduleNotificationTest1(){
        // given
        NotificationEvents event = NotificationEvents.builder()
                .receiverId(1L)
                .message("일정이 생성되었습니다.")
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .notificationChannel(NotificationChannel.WEB)
                .createdTime(LocalDateTime.now())
                .build();

        // when
        scheduleTemplate.send("notification-events", event);

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var result = notificationService.getNotificationsByUserId(1L);
            assertThat(result).isNotEmpty();
            assertThat(result.get(0).getMessage()).contains("일정이 생성되었습니다.");
        });
    }

    @Test
    @DisplayName("일정 알림이 DLQ로 이동이 되는지 테스트")
    public void ScheduleNotificationDLQTest(){
        dlqTestConsumer.clear();

        NotificationEvents event = NotificationEvents.builder()
                .receiverId(999L)
                .message("강제실패 알림")
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .notificationChannel(NotificationChannel.WEB)
                .createdTime(LocalDateTime.now())
                .build();

        scheduleTemplate.send("notification-events", event);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<NotificationEvents> messages = dlqTestConsumer.getNotificationDlqMessages();
                    assertThat(messages).isNotEmpty();
                    assertThat(messages.get(0).getMessage()).contains("강제실패 알림");
                });
    }

    @Test
    @DisplayName("일정 알림이 DLQ로 이동후 재처리후 실패이력에 저장후 복구 되는지 테스트")
    public void ScheduleNotificationDLQRetryTest(){

        // 1. 실패 유도: 강제 예외 발생시킬 메시지
        NotificationEvents event = NotificationEvents.builder()
                .receiverId(999L)
                .message("강제실패 알림")
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .notificationChannel(NotificationChannel.WEB)
                .createdTime(LocalDateTime.now())
                .build();

        scheduleTemplate.send("notification-events", event);

        // 2. DLQ → 실패 이력 저장까지 확인
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var failList = failedMessageService.findByResolvedFalse();
                    assertThat(failList).isNotEmpty();
                    System.out.println(failList.get(0).getPayload());
                  });

        // 3. 재처리 호출
        notificationDlqRetryScheduler.retryNotifications();

        verify(notificationDlqRetryScheduler, times(1)).retryNotifications();

        // 4. 재처리된 결과 확인 (알림 DB에 저장되었는지)
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var notiList = notificationService.getNotificationsByUserId(999L);
                    assertThat(notiList).isNotEmpty();
                    assertThat(notiList.get(0).getMessage()).contains("강제실패 알림");
                });
    }


    @Test
    @DisplayName("Outbox → Kafka → Consumer: 회원가입 알림 전파 검증")
    void memberOutboxToKafkaIntegrationTest() {
        // 1. 회원가입 Kafka 이벤트 생성
        MemberSignUpKafkaEvent event = MemberSignUpKafkaEvent
                .of(555L,"outboxUser","outbox@test.com");

        // 2. Outbox 저장
        outboxEventService.saveEvent(
                event,
                AggregateType.MEMBER.name(),
                "555",
                EventType.SIGNED_UP_WELCOME.name()
        );

        // 3. OutboxPublisher 수동 실행
        outboxEventPublisher.publishOutboxEvents();

        // 4. Outbox 상태 확인
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var events = outboxEventRepository.findAll();
                    assertThat(events).isNotEmpty();
                    assertThat(events.get(0).getSent()).isTrue();
                });

        // 5. 알림 저장 확인
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var notiList = notificationService.getNotificationsByUserId(555L);
                    assertThat(notiList).isNotEmpty();
                    assertThat(notiList.get(0).getMessage()).contains("🎉 환영합니다, outboxUser님! 회원가입이 완료되었습니다.");
                });
    }

    @Test
    @DisplayName("Outbox → Kafka → Consumer: 일정 생성 알림 전파 검증")
    void scheduleOutboxToKafkaIntegrationTest() {
        // 1. 일정 Kafka 이벤트 생성
        NotificationEvents event = NotificationEvents.builder()
                .receiverId(888L)
                .message("일정 알림")
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .notificationChannel(NotificationChannel.WEB)
                .createdTime(LocalDateTime.now())
                .build();

        // 2. Outbox 저장
        outboxEventService.saveEvent(
                event,
                AggregateType.SCHEDULE.name(),
                "888",
                EventType.SCHEDULE_CREATED.name()
        );

        // 3. OutboxPublisher 수동 실행
        outboxEventPublisher.publishOutboxEvents();

        // 4. Outbox 상태 확인
        await().atMost(10,TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var events = outboxEventRepository.findAll();
                    assertThat(events).isNotEmpty();
                    assertThat(events.get(0).getSent()).isTrue();
                    assertThat(events.get(0).getSentAt()).isNotNull();
                });

        // 5. 알림 저장 확인
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var notiList = notificationService.getNotificationsByUserId(888L);
                    assertThat(notiList).isNotEmpty();
                    assertThat(notiList.get(0).getMessage()).contains("일정 알림");
                });
    }

    @Test
    @DisplayName("알림 설정이 꺼져 있을 경우 알림이 저장되지 않아야 함")
    void notificationSettingDisabled_ShouldNotStoreNotification() {
        // given
        Long userId = 777L;

        // 알림 설정을 false로 강제로 저장 (WEB 비활성화)
        notificationSettingService.updateSetting(NotificationSettingModel.builder()
                .userId(userId)
                .webEnabled(false)
                .emailEnabled(false)
                .pushEnabled(false)
                .scheduleCreatedEnabled(true) // 액션은 켜둠
                .scheduleUpdatedEnabled(true)
                .scheduleDeletedEnabled(true)
                .scheduleRemindEnabled(true)
                .build());

        NotificationEvents event = NotificationEvents.builder()
                .receiverId(userId)
                .message("알림 꺼짐 테스트")
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .notificationChannel(NotificationChannel.WEB) // 꺼진 채널
                .createdTime(LocalDateTime.now())
                .build();

        // when
        scheduleTemplate.send("notification-events", event);

        // then
        await().during(5, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var result = notificationService.getNotificationsByUserId(userId);
                    assertThat(result).isEmpty();

                });
    }

    @Test
    @DisplayName("Outbox → Kafka → Consumer: 일정 리마인드 알림 전파 검증")
    void scheduleReminderOutboxToKafkaIntegrationTest() {
        // 1. Reminder Notification 정보 DB에 추가
        NotificationModel model = NotificationModel.builder()
                .userId(777L)
                .scheduleId(1001L)
                .message("만남 연락 호출")
                .notificationType(NotificationType.SCHEDULE_REMINDER)
                .scheduledAt(LocalDateTime.now().minusMinutes(2))
                .isRead(false)
                .isSent(false)
                .build();

        // NotificationService 통해 DB에 저장
        notificationService.createNotification(model);

        // 2. ReminderNotificationService 실행 (리마인드 저장 + outbox에 event 추가)
        reminderNotificationService.sendReminderNotifications();

        // 3. Outbox Publisher 실행 (Kafka에 보내기)
        outboxEventPublisher.publishOutboxEvents();

        // 4. Outbox 상태 확인
        await().atMost(10,TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var events = outboxEventRepository.findAll();
                    assertThat(events).isNotEmpty();
                    assertThat(events.get(0).getSent()).isTrue();
                    assertThat(events.get(0).getSentAt()).isNotNull();
                });

        // 5. Consumer 발송 결과로 알림 저장이 되어있는지 확인
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var notiList = notificationService.getNotificationsByUserId(777L);
                    assertThat(notiList).isNotEmpty();
                    assertThat(notiList.get(0).getMessage()).contains("만남 연락 호출");
                });
    }

    @Test
    @DisplayName("리마인드 알림이 조건에 맞을 때만 발송되고 저장되는지 검증")
    void reminderNotificationTriggerTest() throws InterruptedException {
        // given - 알림 두 개 생성: 하나는 전송 가능 시간 도달, 하나는 아직 도달 안 함
        NotificationModel readyToSend = NotificationModel.builder()
                .userId(777L)
                .scheduleId(2001L)
                .message("🚨 리마인드 대상 알림")
                .notificationType(NotificationType.SCHEDULE_REMINDER)
                .scheduledAt(LocalDateTime.now().minusMinutes(2)) // 이미 도달
                .isRead(false)
                .isSent(false)
                .build();

        NotificationModel notReadyYet = NotificationModel.builder()
                .userId(777L)
                .scheduleId(2002L)
                .message("❌ 아직 도달 안 한 알림")
                .notificationType(NotificationType.SCHEDULE_REMINDER)
                .scheduledAt(LocalDateTime.now().plusHours(1)) // 아직 도달 안함
                .isRead(false)
                .isSent(false)
                .build();

        // 알림 두 개 저장
        notificationService.createNotification(readyToSend);
        notificationService.createNotification(notReadyYet);

        // when - 리마인드 전송 시도
        reminderNotificationService.sendReminderNotifications();
        outboxEventPublisher.publishOutboxEvents();

        // then - Kafka 통해 Consumer 저장 확인
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    em.clear(); // 강제 초기화

                    List<NotificationModel> resultList = notificationService.getNotificationsByUserId(777L);
                    assertThat(resultList).isNotEmpty();

                    NotificationModel sentNotification = resultList.stream()
                            .filter(n -> n.getMessage().contains("🚨"))
                            .findFirst()
                            .orElseThrow();

                    boolean sentWrongly = resultList.stream()
                            .anyMatch(n -> n.getMessage().contains("❌") && n.isSent());
                    assertThat(sentWrongly).isFalse();
                });
    }

    @Test
    @DisplayName("NotificationEvents 중복 이벤트는 무시되어야 한다")
    void notificationEventsDuplicateEventShouldBeIgnored() {
        String eventId = UUID.randomUUID().toString();

        NotificationEvents event1 = NotificationEvents.builder()
                .receiverId(1000L)
                .message("중복 이벤트 테스트")
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .notificationChannel(NotificationChannel.WEB)
                .createdTime(LocalDateTime.now())
                .build();

        event1.setEventId(eventId);

        NotificationEvents event2 = NotificationEvents.builder()
                .receiverId(1000L)
                .message("중복 이벤트 테스트")
                .notificationType(ScheduleActionType.SCHEDULE_CREATED)
                .notificationChannel(NotificationChannel.WEB)
                .createdTime(LocalDateTime.now())
                .build();

        event2.setEventId(eventId);

        // 동일 이벤트 2번 발행
        scheduleTemplate.send("notification-events", event1);
        scheduleTemplate.send("notification-events", event2);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var result = notificationService.getNotificationsByUserId(1000L);
                    assertThat(result).hasSize(1); // 중복 저장 금지
                    assertThat(result.get(0).getMessage()).contains("중복 이벤트 테스트");
                });
    }

    @Test
    @DisplayName("MemberSignUp 이벤트 중복은 무시되어야 한다")
    void memberSignUpDuplicateEventShouldBeIgnored() {
        String eventId = UUID.randomUUID().toString();

        MemberSignUpKafkaEvent event1 = MemberSignUpKafkaEvent.builder()
                .receiverId(2000L)
                .username("dupUser")
                .email("dup@test.com")
                .message("🎉 환영합니다, dupUser님! 회원가입이 완료되었습니다.")
                .notificationType("SIGN_UP")
                .createdTime(LocalDateTime.now())
                .build();

        event1.setEventId(eventId);

        MemberSignUpKafkaEvent event2 = MemberSignUpKafkaEvent.builder()
                .receiverId(2000L)
                .username("dupUser")
                .email("dup@test.com")
                .message("🎉 환영합니다, dupUser님! 회원가입이 완료되었습니다.")
                .notificationType("SIGN_UP")
                .createdTime(LocalDateTime.now())
                .build();

        event2.setEventId(eventId);

        kafkaTemplate.send("member-signup-events", event1);
        kafkaTemplate.send("member-signup-events", event2);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var result = notificationService.getNotificationsByUserId(2000L);
                    assertThat(result).hasSize(1); // 중복 insert 차단
                    assertThat(result.get(0).getMessage()).contains("🎉 환영합니다, dupUser님! 회원가입이 완료되었습니다.");
                });
    }

    @Test
    @DisplayName("동시성 테스트: 동일 EventId 메시지 10개 동시 처리 시 1건만 성공해야 함")
    void concurrentIdempotencyTest() throws InterruptedException {
        // given
        int threadCount = 10;
        String duplicateEventId = "TEST-EVENT-" + UUID.randomUUID();
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 성공/실패 카운트 (원자성 보장)
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    // 멱등성 로직 호출 (eventId 기반 저장)
                    processedEventService.saveProcessedEvent(duplicateEventId);
                    successCount.getAndIncrement();
                } catch (Exception e) {
                    // Unique 제약 조건 위반 등으로 에러가 나야 정상
                    failCount.getAndIncrement();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, TimeUnit.SECONDS);

        // then
        log.info("성공 횟수: {}, 실패(차단) 횟수: {}", successCount.get(), failCount.get());

        assertThat(successCount.get()).isEqualTo(1); // 오직 1개만 DB에 박혀야 함
        assertThat(failCount.get()).isEqualTo(9);    // 9개는 반드시 튕겨나가야 함
    }

}
