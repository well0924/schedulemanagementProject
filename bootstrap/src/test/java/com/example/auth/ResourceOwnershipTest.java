package com.example.auth;

import com.example.apimodel.notification.NotificationPushApiModel;
import com.example.attach.dto.AttachErrorCode;
import com.example.attach.exception.AttachCustomExceptionHandler;
import com.example.category.dto.CategoryErrorCode;
import com.example.category.exception.CategoryCustomException;
import com.example.events.outbox.OutboxEventService;
import com.example.exception.dto.MemberErrorCode;
import com.example.exception.exception.MemberCustomException;
import com.example.exception.notification.dto.NotificationErrorCode;
import com.example.exception.notification.exception.NotificationCustomException;
import com.example.inbound.notification.NotificationPushInConnector;
import com.example.interfaces.attach.AmazonS3Port;
import com.example.interfaces.attach.AttachRepositoryPort;
import com.example.interfaces.category.CategoryRepositoryPort;
import com.example.interfaces.member.MemberRepositoryPort;
import com.example.model.attach.AttachModel;
import com.example.model.auth.CustomMemberDetails;
import com.example.model.category.CategoryModel;
import com.example.model.member.MemberModel;
import com.example.notification.mapper.NotificationMapper;
import com.example.notification.service.NotificationService;
import com.example.notification.service.PushSubscriptionService;
import com.example.outbound.notification.NotificationOutConnector;
import com.example.service.attach.AttachService;
import com.example.service.category.CategoryService;
import com.example.service.member.MemberService;
import com.example.service.member.guard.MemberGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// URL 규칙만으로는 막을 수 없는 "이 데이터의 주인이 나인가" 확인 (알림 읽음, 첨부 삭제, 카테고리 수정·삭제, 회원 조회, 푸시 구독)
class ResourceOwnershipTest {

    static void loginAs(long memberId, String userId, String role) {
        CustomMemberDetails principal = CustomMemberDetails.builder()
                .memberModel(MemberModel.builder().id(memberId).userId(userId).build())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("알림 읽음 처리")
    class Notice {
        NotificationOutConnector out = mock(NotificationOutConnector.class);
        NotificationService service = new NotificationService(out);

        @Test
        @DisplayName("본인 알림이면 읽음 처리")
        void owner() {
            loginAs(1L, "user1", "ROLE_USER");
            when(out.findUserIdById(10L)).thenReturn(1L);

            service.markAsRead(10L);

            verify(out).markAsRead(10L);
        }

        @Test
        @DisplayName("남의 알림이면 403, 읽음 처리 안 함")
        void other() {
            loginAs(1L, "user1", "ROLE_USER");
            when(out.findUserIdById(10L)).thenReturn(2L);

            assertThatThrownBy(() -> service.markAsRead(10L))
                    .isInstanceOf(NotificationCustomException.class)
                    .satisfies(e -> assertThat(((NotificationCustomException) e).getErrorCode())
                            .isEqualTo(NotificationErrorCode.NOT_NOTIFICATION_OWNER));
            verify(out, never()).markAsRead(anyLong());
        }

        @Test
        @DisplayName("관리자는 남의 알림도 처리")
        void admin() {
            loginAs(99L, "admin", "ROLE_ADMIN");
            when(out.findUserIdById(10L)).thenReturn(2L);

            service.markAsRead(10L);

            verify(out).markAsRead(10L);
        }
    }

    @Nested
    @DisplayName("첨부파일 삭제")
    class Attach {
        AmazonS3Port s3 = mock(AmazonS3Port.class);
        AttachRepositoryPort repo = mock(AttachRepositoryPort.class);
        AttachService service = new AttachService(s3, repo, mock(ApplicationEventPublisher.class));

        AttachModel uploadedBy(String userId) {
            return AttachModel.builder().id(5L).storedFileName("final/a.jpg").createdBy(userId).build();
        }

        @Test
        @DisplayName("본인이 올린 파일이면 S3·DB에서 삭제")
        void owner() {
            loginAs(1L, "user1", "ROLE_USER");
            when(repo.findById(5L)).thenReturn(uploadedBy("user1"));

            service.deleteAttachAndFile(5L);

            verify(s3).delete("final/a.jpg");
            verify(repo).deleteAttach(5L);
        }

        @Test
        @DisplayName("남이 올린 파일이면 403, 아무것도 지우지 않음")
        void other() {
            loginAs(1L, "user1", "ROLE_USER");
            when(repo.findById(5L)).thenReturn(uploadedBy("user2"));

            assertThatThrownBy(() -> service.deleteAttachAndFile(5L))
                    .isInstanceOf(AttachCustomExceptionHandler.class)
                    .satisfies(e -> assertThat(((AttachCustomExceptionHandler) e).getErrorCode())
                            .isEqualTo(AttachErrorCode.NOT_ATTACH_OWNER));
            verify(s3, never()).delete(anyString());
            verify(repo, never()).deleteAttach(anyLong());
        }
    }

    @Nested
    @DisplayName("카테고리 수정·삭제")
    class Category {
        CategoryRepositoryPort port = mock(CategoryRepositoryPort.class);
        CategoryService service = new CategoryService(port);

        CategoryModel createdBy(String userId) {
            return CategoryModel.builder().id(3L).name("회의").depth(0L).createdBy(userId).build();
        }

        @Test
        @DisplayName("만든 사람이면 삭제")
        void owner_delete() {
            loginAs(1L, "user1", "ROLE_USER");
            when(port.findById(3L)).thenReturn(createdBy("user1"));

            service.deleteCategory(3L);

            verify(port).deleteCategory(3L);
        }

        @Test
        @DisplayName("다른 사람이 만든 카테고리는 수정·삭제 403")
        void other() {
            loginAs(1L, "user1", "ROLE_USER");
            when(port.findById(3L)).thenReturn(createdBy("user2"));

            assertThatThrownBy(() -> service.deleteCategory(3L))
                    .isInstanceOf(CategoryCustomException.class)
                    .satisfies(e -> assertThat(((CategoryCustomException) e).getErrorCode())
                            .isEqualTo(CategoryErrorCode.NOT_CATEGORY_OWNER));
            assertThatThrownBy(() -> service.updateCategory(3L, CategoryModel.builder().name("새이름").build()))
                    .isInstanceOf(CategoryCustomException.class);
            verify(port, never()).deleteCategory(anyLong());
        }

        @Test
        @DisplayName("관리자는 삭제 가능")
        void admin() {
            loginAs(99L, "admin", "ROLE_ADMIN");
            when(port.findById(3L)).thenReturn(createdBy("user2"));

            service.deleteCategory(3L);

            verify(port).deleteCategory(3L);
        }
    }

    @Nested
    @DisplayName("회원 상세 조회")
    class Member {
        MemberRepositoryPort repo = mock(MemberRepositoryPort.class);
        MemberService service = new MemberService(repo, mock(OutboxEventService.class), new MemberGuard());

        @Test
        @DisplayName("본인 정보는 조회")
        void owner() {
            loginAs(1L, "user1", "ROLE_USER");
            when(repo.findById(1L)).thenReturn(MemberModel.builder().id(1L).build());

            assertThat(service.findById(1L).getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("다른 회원 정보는 403")
        void other() {
            loginAs(1L, "user1", "ROLE_USER");

            assertThatThrownBy(() -> service.findById(2L))
                    .isInstanceOf(MemberCustomException.class)
                    .satisfies(e -> assertThat(((MemberCustomException) e).getErrorCode())
                            .isEqualTo(MemberErrorCode.NOT_MEMBER_OWNER));
            verify(repo, never()).findById(anyLong());
        }
    }

    @Test
    @DisplayName("웹 푸시 구독: body의 memberId가 아니라 로그인한 회원으로 저장")
    void pushSubscribe_usesLoggedInMember() {
        loginAs(1L, "user1", "ROLE_USER");
        PushSubscriptionService pushService = mock(PushSubscriptionService.class);
        NotificationPushInConnector connector = new NotificationPushInConnector(pushService, mock(NotificationMapper.class));

        connector.subscribe(new NotificationPushApiModel.NotificationPushRequest(2L, "endpoint", "p256dh", "auth", "ua"));

        verify(pushService).saveSubscription(eq(1L), eq("endpoint"), eq("p256dh"), eq("auth"), eq("ua"));
    }
}
