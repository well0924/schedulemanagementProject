package com.example.apiclient.config.kafka;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.kafka.NotificationEvents;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * 알림(Notification) 전송을 위한 Producer 설정
     */
    @Bean
    public ProducerFactory<String, NotificationEvents> notificationProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, NotificationEvents> notificationKafkaTemplate() {
        // 공통 설정이 적용된 genericProducerFactory 사용
        return new KafkaTemplate<>(genericProducerFactory(NotificationEvents.class));
    }

    @Bean
    public KafkaTemplate<String, MemberSignUpKafkaEvent> memberKafkaTemplate() {
        return new KafkaTemplate<>(genericProducerFactory(MemberSignUpKafkaEvent.class));
    }

    /**
     * 범용 Object 전송용 Template
     * Primary 선언으로 기본 Template으로 지정
     */
    @Primary
    @Bean(name = "objectKafkaTemplate")
    public KafkaTemplate<String, Object> objectKafkaTemplate() {
        // acks=all과 idempotence=true
        return new KafkaTemplate<>(genericProducerFactory(Object.class));
    }

    /**
     * 공통 Producer 설정
     */
    private <T> ProducerFactory<String, T> genericProducerFactory(Class<T> clazz) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // 데이터 유실 방지
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        // 중복 발행 방지
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // 헤더에 타입 정보를 포함시켜 컨슈머의 역직렬화 도우미 역할 (다형성 처리)
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        return new DefaultKafkaProducerFactory<>(config);
    }
}
