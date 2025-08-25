package com.example.apiclient.config.websocket;

import com.example.service.auth.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

@Slf4j
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String jwtToken = extractJwtFromMessage(accessor);
            //토큰이 없으면 그냥 통과한다.(ex:회원가입)
            if (jwtToken == null) {
                return message;
            }

            //JWT 검증 후 memberId 추출
            Claims claims = jwtTokenProvider.parseClaims(jwtToken);

            Long memberId = claims.get("memberId", Long.class);
            if (claims == null || memberId == null) {
                accessor.setLeaveMutable(true);
                throw new RuntimeException("Invalid JWT Payload: Missing memberId");
            }

            // WebSocket 세션에 memberId 저장 (이후 컨트롤러에서 활용 가능)
            accessor.getSessionAttributes().put("memberId", memberId);

            log.info("[WebSocketAuthInterceptor] 인증 성공, memberId=" + memberId);
        }


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
