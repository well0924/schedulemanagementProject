package com.example.scheduler;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import com.example.inbound.consumer.member.MemberSignUpDlqRetryScheduler;
import com.example.inbound.consumer.schedule.NotificationDlqRetryScheduler;
import com.example.notification.model.FailMessageModel;
import com.example.notification.service.FailedMessageService;
import com.redis.testcontainers.RedisContainer;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.backoff.FixedBackOff;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
public class DlqRetrySchedulerTest {

    @Container
    static final RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7.0"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort())
            .withStartupAttempts(3);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.0"));

    @DynamicPropertySource
    static void setRedisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> kafka.getBootstrapServers());
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    KafkaTemplate<String, MemberSignUpKafkaEvent> memberKafkaTemplate;

    @Autowired
    KafkaTemplate<String,NotificationEvents> notificationKafkaTemplate;

    @Autowired
    private FailedMessageService failedMessageService;

    @Autowired
    ApplicationContext context;


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
                @Qualifier("notificationEventsProducerFactory")
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

    @TestConfiguration
    @EnableScheduling
    @EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
    static class ShedLockTestConfig {

        @Bean
        public LockProvider lockProvider(RedisConnectionFactory redisConnectionFactory) {
            return new RedisLockProvider(redisConnectionFactory,"shedlock");
        }
    }

    @BeforeAll
    static void setUp() {

        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        try (AdminClient adminClient = AdminClient.create(config)) {
            List<NewTopic> topics = List.of(
                    new NewTopic("member-signup-events", 1, (short) 1),
                    new NewTopic("member-signup-events.DLQ", 1, (short) 1),
                    new NewTopic("member-signup.retry.5s", 1, (short) 1),
                    // ...생략...
                    new NewTopic("notification-events.retry.final", 1, (short) 1)
            );
            try {
                adminClient.createTopics(topics).all().get();
            } catch (ExecutionException e) {
                // 토픽 이미 있으면 무시
                if (e.getCause() instanceof TopicExistsException) {
                    // nothing
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("토픽 생성 실패", e);
        }
    }

    @BeforeEach
    void insertFailMessage() {
        FailMessageModel fail = FailMessageModel.builder()
                .messageType("MEMBER_SIGNUP")
                .payload("{\"receiverId\":999,\"username\":\"retryUser\",\"email\":\"fail@test.com\",\"message\":\"강제실패 알림\",\"notificationType\":null,\"createdTime\":null}") // 실제 필요 데이터 JSON 문자열
                .retryCount(0)
                .resolved(false)
                .createdAt(LocalDateTime.now())
                .build();

        failedMessageService.createFailMessage(fail);
    }

    @Test
    void shedlock_MemberSchedulerTest() {
        // 프록시 bean 얻기
        Object bean = context.getBean(MemberSignUpDlqRetryScheduler.class);
        System.out.println(bean.getClass().getName()); // 프록시 클래스명 출력

        // static 카운트 0으로 초기화
        MemberSignUpDlqRetryScheduler.EXECUTION_COUNT = 0;

        // 두 번 호출
        ((MemberSignUpDlqRetryScheduler)bean).retryMemberSignUps();
        ((MemberSignUpDlqRetryScheduler)bean).retryMemberSignUps();

        // 카운트 확인
        System.out.println("EXECUTION_COUNT = " + MemberSignUpDlqRetryScheduler.EXECUTION_COUNT);
        assertEquals(1, MemberSignUpDlqRetryScheduler.EXECUTION_COUNT);
    }

    @Test
    public void shedlock_scheduleSchedulerTest(){
        // 프록시 bean 얻기
        Object bean = context.getBean(NotificationDlqRetryScheduler.class);
        System.out.println(bean.getClass().getName()); // 프록시 클래스명 출력

        // static 카운트 0으로 초기화
        NotificationDlqRetryScheduler.EXECUTION_COUNT = 0;

        // 두 번 호출
        ((NotificationDlqRetryScheduler)bean).retryNotifications();
        ((NotificationDlqRetryScheduler)bean).retryNotifications();

        // 카운트 확인
        System.out.println("EXECUTION_COUNT = " + NotificationDlqRetryScheduler.EXECUTION_COUNT);
        assertEquals(1, NotificationDlqRetryScheduler.EXECUTION_COUNT);
    }
}
