package com.example.jpa.config.jpa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

@Slf4j
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {

        return Optional.of(getUserName());
    }

    public String getUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymousUser"; // 인증 안된 경우 기본값 (혹은 null)
        }

        Object principal = authentication.getPrincipal();
        log.debug("Principal: {}", principal);

        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername(); // UserDetails 기반이면 정상
        } else if (principal instanceof String) {
            return (String) principal; // 그냥 String (ex: "anonymousUser") 경우
        } else {
            return "unknown"; // 예상치 못한 타입
        }
    }
}
