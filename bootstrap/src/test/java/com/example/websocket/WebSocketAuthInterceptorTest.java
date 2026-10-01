package com.example.websocket;

import com.example.service.auth.jwt.JwtTokenProvider;
import com.example.webscoket.config.WebSocketAuthInterceptor;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// STOMP CONNECT는 토큰 필수, 회원별 토픽은 본인만 구독 가능한지 확인 (브로커 없이 인터셉터만 검증)
class WebSocketAuthInterceptorTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(jwtTokenProvider);
    private final MessageChannel channel = mock(MessageChannel.class);

    private Message<byte[]> stomp(StompCommand command, String authorization, String destination, Map<String, Object> session) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) accessor.setNativeHeader("Authorization", authorization);
        if (destination != null) accessor.setDestination(destination);
        accessor.setSessionAttributes(session);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void tokenBelongsTo(long memberId) {
        Claims claims = mock(Claims.class);
        given(claims.get("memberId", Long.class)).willReturn(memberId);
        given(jwtTokenProvider.parseClaims(anyString())).willReturn(claims);
    }

    @Test
    @DisplayName("토큰 없이 CONNECT하면 거부한다")
    void connect_withoutToken_rejected() {
        assertThatThrownBy(() -> interceptor.preSend(stomp(StompCommand.CONNECT, null, null, new HashMap<>()), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 CONNECT하면 거부한다")
    void connect_withInvalidToken_rejected() {
        given(jwtTokenProvider.parseClaims(anyString())).willThrow(new RuntimeException("bad token"));

        assertThatThrownBy(() -> interceptor.preSend(stomp(StompCommand.CONNECT, "Bearer bad", null, new HashMap<>()), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("유효한 토큰으로 CONNECT하면 세션에 회원 번호를 저장한다")
    void connect_withToken_storesMemberId() {
        tokenBelongsTo(63L);
        Map<String, Object> session = new HashMap<>();

        interceptor.preSend(stomp(StompCommand.CONNECT, "Bearer ok", null, session), channel);

        assertThat(session).containsEntry("memberId", 63L);
    }

    @Test
    @DisplayName("본인의 챗봇·알림 토픽은 구독할 수 있다")
    void subscribe_ownTopics_allowed() {
        Map<String, Object> session = new HashMap<>(Map.of("memberId", 63L));

        interceptor.preSend(stomp(StompCommand.SUBSCRIBE, null, "/topic/chat/63", session), channel);
        interceptor.preSend(stomp(StompCommand.SUBSCRIBE, null, "/topic/notifications/63", session), channel);
    }

    @Test
    @DisplayName("다른 회원의 챗봇·알림 토픽은 구독할 수 없다")
    void subscribe_othersTopics_rejected() {
        Map<String, Object> session = new HashMap<>(Map.of("memberId", 63L));

        assertThatThrownBy(() -> interceptor.preSend(stomp(StompCommand.SUBSCRIBE, null, "/topic/chat/64", session), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(stomp(StompCommand.SUBSCRIBE, null, "/topic/notifications/64", session), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("인증되지 않은 세션은 회원별 토픽을 구독할 수 없다")
    void subscribe_withoutAuthenticatedSession_rejected() {
        assertThatThrownBy(() -> interceptor.preSend(stomp(StompCommand.SUBSCRIBE, null, "/topic/chat/63", new HashMap<>()), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("회원별 토픽이 아니면 구독을 제한하지 않는다")
    void subscribe_publicTopic_allowed() {
        interceptor.preSend(stomp(StompCommand.SUBSCRIBE, null, "/topic/announcements", new HashMap<>()), channel);
    }
}
