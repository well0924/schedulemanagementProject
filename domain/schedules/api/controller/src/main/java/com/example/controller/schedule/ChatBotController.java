package com.example.controller.schedule;

import com.example.apimodel.schedule.ChatRequest;
import com.example.inbound.schedules.ChatBotMessageSenderPort;
import com.example.inbound.schedules.ScheduleRecommendationConnectorImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatBotController {

    private final ChatBotMessageSenderPort chatBotMessageSenderPort;

    private final ScheduleRecommendationConnectorImpl scheduleRecommendationConnector;

    @PostMapping("/send")
    public ResponseEntity<Void> sendMessage(@RequestBody ChatRequest request) {
        log.info("[ChatBotController] 챗봇 요청 수신: memberId={}", request.memberId());

        // 1. 비즈니스 로직 호출 (Flux<String> 반환)
        Flux<String> tokenStream = scheduleRecommendationConnector.streamChat(
                request.memberId(),
                request.message()
        );

        // 2. 웹소켓 전송 객체에게 스트림 처리를 위임
        // 이 안에서 subscribe가 일어나며 비동기로 클라이언트에게 전달됨
        chatBotMessageSenderPort.send(request.memberId(), tokenStream);

        // 3. HTTP 응답은 즉시 반환 (답변은 웹소켓으로 가니까)
        return ResponseEntity.accepted().build();
    }
}
