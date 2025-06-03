package com.example.apiclient.config.kafka;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
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

//    @Bean
//    public ConsumerFactory<String, NotificationEvents> consumerFactory(Class<T> targetClass) {
//
//        Map<String, Object> config = new HashMap<>();
//        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
//        config.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-group");
//        // DLQ 작동을 위한 역직렬화 설정
//        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class.getName());
//        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class.getName());
//        // 내부에서 실제로 사용할 디시리얼라이저 지정
//        config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class.getName());
//        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
//        //기타 설정.
//        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
//        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, targetClass.getName());
//        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
//        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
//        return config;
//    }

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

    // dlq 설정
    @Bean(name = "notificationKafkaListenerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, NotificationEvents> notificationKafkaListenerFactory(
            KafkaTemplate<String, NotificationEvents> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, NotificationEvents> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(notificationConsumerFactory());

        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition())
                ),
                new FixedBackOff(0L, 3)
        ));

        factory.setMissingTopicsFatal(false);
        return factory;
    }

    @Bean(name = "memberKafkaListenerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, MemberSignUpKafkaEvent> memberKafkaListenerFactory(
            KafkaTemplate<String, MemberSignUpKafkaEvent> kafkaTemplate
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

        factory.setMissingTopicsFatal(false);
        return factory;
    }
}
