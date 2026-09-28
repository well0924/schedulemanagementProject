package com.example.member;

import com.example.events.outbox.OutboxEventService;
import com.example.exception.dto.MemberErrorCode;
import com.example.exception.exception.MemberCustomException;
import com.example.interfaces.member.MemberRepositoryPort;
import com.example.model.member.MemberModel;
import com.example.service.member.MemberService;
import com.example.service.member.guard.MemberGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 회원가입 아이디 중복 방지 (member.user_id 중복 시 해당 계정 로그인 불가 문제)
@ExtendWith(MockitoExtension.class)
class MemberSignUpDuplicateTest {

    @InjectMocks
    MemberService memberService;

    @Mock
    MemberRepositoryPort memberRepositoryPort;
    @Mock
    OutboxEventService outboxEventService;
    @Mock
    MemberGuard memberGuard;

    MemberModel newMember() {
        return MemberModel.builder()
                .userId("loadtest001")
                .userEmail("tester@example.com")
                .userPhone("01012345678")
                .build();
    }

    @Test
    @DisplayName("이미 있는 아이디면 USERID_DUPLICATE, 저장하지 않음")
    void duplicateUserId_rejected() {
        when(memberRepositoryPort.existsByUserId("loadtest001")).thenReturn(true);

        assertThatThrownBy(() -> memberService.createMember(newMember()))
                .isInstanceOf(MemberCustomException.class)
                .satisfies(e -> assertThat(((MemberCustomException) e).getErrorCode())
                        .isEqualTo(MemberErrorCode.USERID_DUPLICATE));

        verify(memberRepositoryPort, never()).createMember(any());
        verify(outboxEventService, never()).saveEvent(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("새 아이디면 저장하고 가입 이벤트를 outbox에 기록")
    void newUserId_created() {
        MemberModel saved = MemberModel.builder()
                .id(1L)
                .userId("loadtest001")
                .userEmail("tester@example.com")
                .userPhone("01012345678")
                .build();
        when(memberRepositoryPort.existsByUserId("loadtest001")).thenReturn(false);
        when(memberRepositoryPort.createMember(any())).thenReturn(saved);

        MemberModel result = memberService.createMember(newMember());

        assertThat(result.getId()).isEqualTo(1L);
        verify(memberRepositoryPort).createMember(any());
        verify(outboxEventService).saveEvent(any(), anyString(), anyString(), anyString());
    }
}
