package com.example.controller.schedule;

import com.example.apimodel.schedule.ChatRequest;
import com.example.inbound.schedules.ChatBotMessageSenderPort;
import com.example.inbound.schedules.ScheduleRecommendationConnector;
import com.example.security.config.SecurityUtil;
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

    private final ScheduleRecommendationConnector scheduleRecommendationConnector;

    @PostMapping("/send")
    public ResponseEntity<Void> sendMessage(@RequestBody ChatRequest request) {
        // 회원 번호는 요청 본문이 아니라 로그인 정보에서 가져온다.
        // 본문 값을 믿으면 다른 회원의 일정·대화 이력으로 챗봇을 호출하고 그 회원 토픽으로 답변을 보낼 수 있다.
        Long memberId = SecurityUtil.currentUserId();
        if (request.memberId() != null && !request.memberId().equals(memberId)) {
            log.warn("[ChatBotController] 요청 본문의 memberId({})를 무시하고 로그인 회원({})으로 처리", request.memberId(), memberId);
        }
        log.info("[ChatBotController] 챗봇 요청 수신: memberId={}", memberId);

        // 1. 비즈니스 로직 호출 (Flux<String> 반환)
        Flux<String> tokenStream = scheduleRecommendationConnector.streamChat(
                memberId,
                request.message()
        );

        // 2. 웹소켓 전송 객체에게 스트림 처리를 위임
        // 이 안에서 subscribe가 일어나며 비동기로 클라이언트에게 전달됨
        chatBotMessageSenderPort.send(memberId, tokenStream);

        // 3. HTTP 응답은 즉시 반환 (답변은 웹소켓으로 가니까)
        return ResponseEntity.accepted().build();
    }
}
