package com.example.kafka;

import com.example.enumerate.member.Roles;
import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import com.example.inbound.consumer.member.MemberSignUpDlqRetryScheduler;
import com.example.inbound.consumer.schedule.NotificationDlqRetryScheduler;
import com.example.kafka.dlq.DlqTestConsumer;
import com.example.model.member.MemberModel;
import com.example.notification.service.FailedMessageService;
import com.example.notification.service.NotificationService;
import com.example.outbound.notification.NotificationOutConnector;
import com.example.service.member.MemberService;
import com.example.service.schedule.ScheduleDomainService;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.backoff.FixedBackOff;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public class KafkaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.0"));

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> kafka.getBootstrapServers());
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    KafkaTemplate<String, MemberSignUpKafkaEvent> kafkaTemplate;

    @Autowired
    KafkaTemplate<String, NotificationEvents> scheduleTemplate;

    @Autowired
    EntityManager em;

    @Autowired
    MemberService memberService;

    @Autowired
    NotificationOutConnector notificationOutConnector;

    @Autowired
    NotificationService notificationService;

    @Autowired
    ScheduleDomainService scheduleDomainService;

    @Autowired
    DlqTestConsumer dlqTestConsumer;

    @Autowired
    MemberSignUpDlqRetryScheduler retryScheduler;

    @Autowired
    NotificationDlqRetryScheduler notificationDlqRetryScheduler;

    @Autowired
    FailedMessageService failedMessageService;

    @TestConfiguration
    static class KafkaTestConfig {

        @Bean
        @Primary
        public ProducerFactory<String, MemberSignUpKafkaEvent> memberProducerFactory() {
            Map<String, Object> config = new HashMap<>();
            config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
            config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
            config.put(org.springframework.kafka.support.serializer.JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
            return new DefaultKafkaProducerFactory<>(config);
        }

        @Bean
        @Primary
        public KafkaTemplate<String, MemberSignUpKafkaEvent> memberKafkaTemplate(
                ProducerFactory<String, MemberSignUpKafkaEvent> pf) {
            return new KafkaTemplate<>(pf);
        }

        @Bean
        public DefaultErrorHandler errorHandler(KafkaTemplate<String,MemberSignUpKafkaEvent> template) {
            var recovered = new DeadLetterPublishingRecoverer(template,
                    (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition()));
            var backoff = new FixedBackOff(1000L, 1); // 즉시 재시도 1회
            return new DefaultErrorHandler(recovered, backoff);
        }

        @Bean(name = "memberKafkaListenerFactory")
        @Primary
        public ConcurrentKafkaListenerContainerFactory<String, MemberSignUpKafkaEvent> memberKafkaListenerFactory() {
            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
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
            config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
            config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
            config.put(org.springframework.kafka.support.serializer.JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
            return new DefaultKafkaProducerFactory<>(config);
        }

        @Bean
        public KafkaTemplate<String,NotificationEvents> notificationKafkaTemplate(
                ProducerFactory<String,NotificationEvents> pf) {
            return new KafkaTemplate<>(pf);
        }

        @Bean
        public DefaultErrorHandler notificationErrorHandler(KafkaTemplate<String, NotificationEvents> template) {
            var recoverer = new DeadLetterPublishingRecoverer(template,
                    (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition()));
            var backoff = new FixedBackOff(1000L, 1);
            return new DefaultErrorHandler(recoverer, backoff);
        }

        @Bean(name = "notificationKafkaListenerFactory")
        public ConcurrentKafkaListenerContainerFactory<String, NotificationEvents> notificationKafkaListenerFactory() {
            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
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
    }

    @BeforeAll
    static void createTopics() throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafka.getBootstrapServers());
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
    @DisplayName("컨슈머에서 실패를 했을경우에 DLQ로 진행이 되는 경우")
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
    @DisplayName("DLQ로 보내진 후 실패이력을 저장을 하고 재처리 하기")
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

        // 4. RetryTopicConsumer → 원래 토픽으로 재전송됐는지 확인
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<MemberSignUpKafkaEvent> messages = dlqTestConsumer.getMemberDlqMessages();
                    assertThat(messages).isNotEmpty();
                    assertThat(messages.get(0).getEmail()).isEqualTo("fail@test.com");
                });
    }

    @Test
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

        // 4. 재처리된 결과 확인 (알림 DB에 저장되었는지)
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var notiList = notificationService.getNotificationsByUserId(999L);
                    assertThat(notiList).isNotEmpty();
                    assertThat(notiList.get(0).getMessage()).contains("강제실패 알림");
                });
    }
}
