package com.example.schedule.apiclient.config;

import com.example.service.auth.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            throw new RuntimeException("Invalid STOMP Message");
        }

        String jwtToken = extractJwtFromMessage(accessor);
        //토큰이 없으면 그냥 통과한다.(ex:회원가입)
        if (jwtToken == null) {
            return message;
        }

        //JWT 검증 후 userId 추출
        Claims claims = jwtTokenProvider.parseClaims(jwtToken);

        if (claims == null) {
            throw new RuntimeException("Invalid JWT Token");
        }

        Long userId = claims.get("userId", Long.class);
        if (userId == null) {
            throw new RuntimeException("Invalid JWT Payload: Missing UserId");
        }

        // WebSocket 세션에 userId 저장 (메시지 전송 시 사용 가능)
        accessor.getSessionAttributes().put("userId", userId);

        return message;
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
