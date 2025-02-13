package com.example.api.auth;

import com.example.apimodel.auth.LoginDto;
import com.example.apimodel.auth.RequestRefreshTokenDto;
import com.example.apimodel.auth.TokenResponse;
import com.example.inconnector.auth.AuthInConnector;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthInConnector authInConnector;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse>login(@Validated @RequestBody LoginDto loginDto) {
        TokenResponse loginResult = authInConnector.login(loginDto);
        log.info("loginResult::"+loginResult);
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
        log.info("reissue::"+reissueResult);
        return ResponseEntity.status(HttpStatus.OK).body(reissueResult);
    }
}
