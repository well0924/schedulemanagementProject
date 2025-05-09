package com.example.api.auth;

import com.example.apimodel.auth.LoginDto;
import com.example.apimodel.auth.RequestRefreshTokenDto;
import com.example.apimodel.auth.TokenResponse;
import com.example.inbound.auth.AuthInConnector;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthInConnector authInConnector;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse>login(@Validated @RequestBody LoginDto loginDto) {
        TokenResponse loginResult = authInConnector.login(loginDto);
        return ResponseEntity.status(HttpStatus.OK).body(loginResult);
    }

    @PostMapping("/log-out")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String accessToken) {
        authInConnector.logout(accessToken);
        return ResponseEntity.status(HttpStatus.OK).body("Log-out");
    }

    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse>tokenReissue(@Validated @RequestBody RequestRefreshTokenDto refreshTokenDto) {
        TokenResponse reissueResult = authInConnector.tokenReissue(refreshTokenDto);
        return ResponseEntity.status(HttpStatus.OK).body(reissueResult);
    }

    //현재회원의 번호
    @GetMapping("/user-id")
    public ResponseEntity<Long> currentUserMember(@RequestHeader("Authorization") String accessToken){
        Long userId = authInConnector.currentUserId(accessToken);
        return ResponseEntity.status(HttpStatus.OK).body(userId);
    }
}
