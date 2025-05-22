package com.example.apiclient.config.kafka;

import com.example.events.kafka.MemberSignUpKafkaEvent;
import com.example.events.spring.MemberSignUpEvent;
import com.example.events.kafka.NotificationEvents;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {


    public <T> ProducerFactory<String, T> genericProducerFactory(Class<T> clazz) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, NotificationEvents> notificationKafkaTemplate() {
        return new KafkaTemplate<>(genericProducerFactory(NotificationEvents.class));
    }

    @Bean
    public KafkaTemplate<String, MemberSignUpKafkaEvent> memberKafkaTemplate() {
        return new KafkaTemplate<>(genericProducerFactory(MemberSignUpKafkaEvent.class));
    }
}
