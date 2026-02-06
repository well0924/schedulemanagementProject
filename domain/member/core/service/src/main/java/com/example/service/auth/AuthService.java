package com.example.service.auth;

import com.example.model.auth.CustomMemberDetails;
import com.example.outbound.auth.AuthOutConnector;
import com.example.service.auth.jwt.JwtTokenProvider;
import com.example.service.auth.jwt.TokenDto;
import io.jsonwebtoken.Claims;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final RedisService redisService;
    private final AuthOutConnector auth;

    //로그인
    public TokenDto login(String userId, String password) {
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
                new UsernamePasswordAuthenticationToken(userId,password);

        Authentication authentication = authenticationManager.authenticate(usernamePasswordAuthenticationToken);

        TokenDto tokenDto = jwtTokenProvider.generateToken(authentication);
        // Refresh Token 캐싱
        redisService.saveRefreshToken(authentication.getName(), tokenDto.getRefreshToken());
        return tokenDto;
    }

    //로그아웃
    public void logout(String accessToken) {
        if (jwtTokenProvider.validateToken(accessToken)) {
            Claims claims = jwtTokenProvider.parseClaims(accessToken);
            String userId = claims.getSubject();

            // Access Token을 블랙리스트에 저장
            long expiration = jwtTokenProvider.getExpiration(accessToken);
            redisService.saveBlacklistToken(accessToken,"logged_out",expiration);
            // Refresh Token 삭제
            redisService.deleteRefreshToken(userId);
            SecurityContextHolder.clearContext();
        } else {
            throw new IllegalArgumentException("유효하지 않은 Access Token입니다.");
        }
    }

    //토큰 재발급
    public TokenDto tokenReissue(String accessToken,String refreshToken){
        if(!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        Authentication authentication = jwtTokenProvider.getAuthentication(accessToken);

        String storedToken = redisService.findRefreshToken(authentication.getName());

        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new RuntimeException("일치하지 않는 Refresh Token입니다.");
        }

        TokenDto newToken = jwtTokenProvider.generateToken(authentication);
        // 5. Redis에 새로운 Refresh Token 저장
        redisService.saveRefreshToken(authentication.getName(),refreshToken);
        return newToken;
    }

    public Long currentUserId(String accessToken){
        String token = accessToken;

        if ( accessToken != null &&  accessToken.startsWith("Bearer ")) {
            token =  accessToken.substring(7);
        }
        String userId = jwtTokenProvider.getAuthentication(token).getName();
        CustomMemberDetails customMemberDetails = (CustomMemberDetails) auth.loadUserByUsername(userId);
        return customMemberDetails.getMemberModel().getId();
    }
}
