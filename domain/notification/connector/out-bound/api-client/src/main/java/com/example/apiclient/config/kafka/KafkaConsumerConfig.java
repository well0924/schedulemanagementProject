package com.example.apiclient.config.kafka;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;


@Configuration
public class KafkaConsumerConfig {


    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * 컨슈머 공통 설정: 역직렬화 에러 처리 및 신뢰성 있는 메시지 수신용
     */
    private <T> Map<String, Object> consumerConfigs(Class<T> targetClass) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class.getName());
        config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class.getName());
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, targetClass.getName());
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return config;
    }

    @Bean
    public ConsumerFactory<String, NotificationEvents> notificationConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs(NotificationEvents.class),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(new JsonDeserializer<>(NotificationEvents.class, false)));
    }

    @Bean
    public ConsumerFactory<String, MemberSignUpKafkaEvent> memberSignUpConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs(MemberSignUpKafkaEvent.class),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(new JsonDeserializer<>(MemberSignUpKafkaEvent.class, false)));
    }

    /**
     * 알림 서비스용 리스너 팩토리 (DLQ 및 수동 커밋 설정 포함)
     */
    @Bean(name = "notificationKafkaListenerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, NotificationEvents> notificationKafkaListenerFactory(
            @Qualifier("notificationKafkaTemplate") KafkaTemplate<String, NotificationEvents> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, NotificationEvents> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(notificationConsumerFactory());

        // 예외 발생 시 재시도 및 DLQ 전송 로직 제어
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        // 원본 토픽명 뒤에 ".DLQ"를 붙여 실패 메시지 격리 (partition 유지)
                        (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition())
                ),
                // 2초간격으로 3회 재시도 후 DLQ로 전송
                new FixedBackOff(2000L, 3)
        ));
        //MANUAL_IMMEDIATE: 비즈니스 로직 성공 후 즉시 오프셋을 커밋하여 데이터 정합성 강화
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // 시작 시 토픽이 없어도 애플리케이션 실행 허용
        factory.setMissingTopicsFatal(false);
        factory.setConcurrency(2);
        return factory;
    }

    @Bean(name = "memberKafkaListenerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, MemberSignUpKafkaEvent> memberKafkaListenerFactory(
            @Qualifier("memberKafkaTemplate") KafkaTemplate<String, MemberSignUpKafkaEvent> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, MemberSignUpKafkaEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(memberSignUpConsumerFactory());

        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition())
                ),
                new FixedBackOff(0L, 3)
        ));
        //MANUAL_IMMEDIATE: 비즈니스 로직 성공 후 즉시 오프셋을 커밋하여 데이터 정합성 강화
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setMissingTopicsFatal(false);
        factory.setConcurrency(2);
        return factory;
    }
}
