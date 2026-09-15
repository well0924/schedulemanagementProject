package com.example.service.member.guard;

import com.example.exception.dto.MemberErrorCode;
import com.example.exception.exception.MemberCustomException;
import com.example.model.auth.CustomMemberDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * ScheduleGuard.assertOwnerOrAdmin / SecurityUtil.currentUserId()+hasRole()와 로직이 중복된다.
 * common:security 모듈이 이미 domain:member:core:service를 의존하고 있어서(JwtAuthenticationFilter,
 * AuthService 등이 이 모듈에 있음), 여기서 반대로 common:security(SecurityUtil)를 의존하면
 * 순환 의존이 발생한다. 그래서 SecurityUtil을 재사용하지 못하고 같은 로직을 이 모듈 안에서
 * (이미 의존 중인 domain:member:core:model의 CustomMemberDetails로) 다시 구현했다.
 * TODO: currentUserId/hasRole 판단 로직을 양쪽이 공통으로 의존 가능한 하위 모듈로 추출해서
 * 중복 제거 - 지금은 이슈 범위(회원 API 권한 수정) 밖이라 보류.
 */
@Component
public class MemberGuard {

    public void assertOwnerOrAdmin(Long targetMemberId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomMemberDetails details)) {
            throw new AccessDeniedException("인증 필요");
        }

        boolean isAdmin = details.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        if (!Objects.equals(details.getMemberModel().getId(), targetMemberId)) {
            throw new MemberCustomException(MemberErrorCode.NOT_MEMBER_OWNER);
        }
    }
}
