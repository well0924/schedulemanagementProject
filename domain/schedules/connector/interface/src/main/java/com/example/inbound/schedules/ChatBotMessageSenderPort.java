package com.example.inbound.schedules;

import reactor.core.publisher.Flux;

public interface ChatBotMessageSenderPort {

    void send(Long memberId, Flux<String> tokenStream);
    void sendClearMessage(Long memberId);
}
