package com.example.test.notification;

import com.example.test.notification.config.KafkaTestBeans;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Import(KafkaTestBeans.class)
@SpringBootTest
public class KafkaBasicTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.0"));

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void kafka연동_테스트컨테이너_기본동작_확인() {
        // test-topic에 메시지 전송 (여기서는 별도 consumer 없이 동작만 확인)
        kafkaTemplate.send("test-topic", "test-key", "testcontainers 연동 OK");
        // 예외 없이 전송만 되면 통과 (기본 연동 성공)
    }
}
