package com.example.chatbot;

import com.example.events.spring.ChatCompletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ChatBotProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private com.example.outbound.producer.ChatBotProducer chatBotProducer;

    @Test
    @DisplayName("Kafka 발행 성공")
    void handle_success() {
        // given
        ChatCompletedEvent event = ChatCompletedEvent.builder()
                .memberId(1L)
                .userMessage("테스트 질문")
                .assistantResponse("테스트 응답")
                .createdAt(LocalDateTime.now())
                .build();

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(Mockito.mock(SendResult.class));
        BDDMockito.given(kafkaTemplate.send(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).willReturn(future);

        // when
        chatBotProducer.handle(event);

        // then
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(
                topicCaptor.capture(),
                keyCaptor.capture(),
                ArgumentMatchers.eq(event)
        );

        assertThat(topicCaptor.getValue()).isEqualTo("chat-history");
        assertThat(keyCaptor.getValue()).isEqualTo("1");
    }
}
