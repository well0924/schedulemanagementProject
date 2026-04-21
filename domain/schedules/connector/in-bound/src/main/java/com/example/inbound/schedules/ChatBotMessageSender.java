package com.example.inbound.schedules;

import com.example.apimodel.schedule.ChatTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatBotMessageSender implements ChatBotMessageSenderPort {

    private final SimpMessagingTemplate messagingTemplate;

    private static final String CHAT_TOPIC = "/topic/chat/";

    @Override
    public void send(Long memberId, Flux<String> tokenStream) {
        tokenStream.subscribe(
                token -> messagingTemplate.convertAndSend(
                        CHAT_TOPIC + memberId,
                        new ChatTokenResponse(token, false)),
                error -> {
                    log.error("[ChatBotMessageSender] 오류 memberId={}", memberId, error);
                    messagingTemplate.convertAndSend(
                            CHAT_TOPIC + memberId,
                            new ChatTokenResponse("오류 발생", true));
                },
                () -> messagingTemplate.convertAndSend(
                        CHAT_TOPIC + memberId,
                        new ChatTokenResponse("", true))
        );
    }

    @Override
    public void sendClearMessage(Long memberId) {
        messagingTemplate.convertAndSend(
                CHAT_TOPIC + memberId,
                new ChatTokenResponse("대화가 초기화되었습니다.", true)
        );
    }
}
