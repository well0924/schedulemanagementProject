package com.example.service.auth;

import com.example.model.auth.CustomMemberDetails;
import lombok.NoArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class SecurityUtil {

    // 로그인한 회원 번호
    public static Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomMemberDetails d)) {
            throw new AccessDeniedException("인증 필요");
        }
        return d.getMemberModel().getId();
    }
    
    // 로그인한 회원 아이디 
    public static String currentUserName() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomMemberDetails d)) {
            throw new AccessDeniedException("인증 필요");
        }
        return d.getMemberModel().getUserId();
    }
    
    // 로그인한 회원 권한
    public static boolean hasRole(String role) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
