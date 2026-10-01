package com.example.webscoket.config;

import com.example.service.auth.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.List;
import java.util.Map;

/**
 * STOMP 인증, 인가.
 * - CONNECT: Authorization 헤더의 JWT가 반드시 있어야 하고, 그 회원 번호를 세션에 저장한다.
 * - SUBSCRIBE: 회원별 토픽(/topic/chat/{id}, /topic/notifications/{id})은 본인 번호만 구독할 수 있다.
 * 예전에는 토큰이 없어도 통과했고 구독 대상도 검사하지 않아, 다른 회원의 챗봇 답변과 알림을 받아볼 수 있었다.
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    static final String SESSION_MEMBER_ID = "memberId";
    static final List<String> MEMBER_TOPIC_PREFIXES = List.of("/topic/chat/", "/topic/notifications/");

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String jwtToken = extractJwtFromMessage(accessor);
        if (jwtToken == null) {
            throw new MessageDeliveryException("STOMP CONNECT에는 Authorization 헤더(Bearer 토큰)가 필요합니다.");
        }

        Long memberId;
        try {
            Claims claims = jwtTokenProvider.parseClaims(jwtToken);
            memberId = claims.get("memberId", Long.class);
        } catch (Exception e) {
            throw new MessageDeliveryException("유효하지 않은 토큰입니다.");
        }
        if (memberId == null) {
            throw new MessageDeliveryException("토큰에 회원 번호가 없습니다.");
        }

        Map<String, Object> session = accessor.getSessionAttributes();
        if (session == null) {
            throw new MessageDeliveryException("웹소켓 세션을 찾을 수 없습니다.");
        }
        session.put(SESSION_MEMBER_ID, memberId);
        log.info("[WebSocketAuthInterceptor] 인증 성공, memberId={}", memberId);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        String prefix = MEMBER_TOPIC_PREFIXES.stream().filter(destination::startsWith).findFirst().orElse(null);
        if (prefix == null) {
            return; // 회원별 토픽이 아니면 제한하지 않는다
        }

        Map<String, Object> session = accessor.getSessionAttributes();
        Object memberId = session == null ? null : session.get(SESSION_MEMBER_ID);
        String target = destination.substring(prefix.length());
        if (memberId == null || !target.equals(String.valueOf(memberId))) {
            log.warn("[WebSocketAuthInterceptor] 구독 거부: destination={}, sessionMemberId={}", destination, memberId);
            throw new MessageDeliveryException("본인의 토픽만 구독할 수 있습니다.");
        }
    }

    //STOMP 메시지에서 헤더에 있는 토큰 추출
    private String extractJwtFromMessage(StompHeaderAccessor accessor) {
        String bearerToken = accessor.getFirstNativeHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // "Bearer " 이후의 토큰 부분만 추출
        }
        return null;
    }
}
