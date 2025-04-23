package com.example.jpa.config.jpa;

import com.example.model.auth.CustomMemberDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Slf4j
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {

        return Optional.of(getUserName());
    }

    public String getUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            log.debug("Principal: {}", principal);

            CustomMemberDetails userDetails = (CustomMemberDetails) authentication.getPrincipal();
            return userDetails.getUsername();
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("Authentication is null or not authenticated. Returning anonymousUser.");
            return "anonymousUser"; // 인증 안 된 경우
        }

        Object principal = authentication.getPrincipal();
        log.debug("Principal: {}", principal);

        if (principal instanceof CustomMemberDetails customMemberDetails) {
            return customMemberDetails.getUsername();
        } else if (principal instanceof String str) {
            return str; // 보통 anonymousUser
        } else {
            log.error("Unknown principal type: {}", principal.getClass());
            return "";
        }
    }
}
