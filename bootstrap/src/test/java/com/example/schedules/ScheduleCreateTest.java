package com.example.schedules;

import com.example.enumerate.schedules.RepeatType;
import com.example.enumerate.schedules.ScheduleType;
import com.example.events.enums.ScheduleActionType;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.interfaces.notification.notification.NotificationInterfaces;
import com.example.model.schedules.SchedulesModel;
import com.example.security.config.SecurityUtil;
import com.example.service.schedule.domainService.ScheduleCreateService;
import com.example.service.schedule.domainService.guard.ScheduleGuard;
import com.example.service.schedule.domainService.repeat.create.RepeatScheduleFactory;
import com.example.service.schedule.domainService.support.AttachBinder;
import com.example.service.schedule.domainService.support.DomainEventPublisher;
import com.example.service.schedule.domainService.support.ScheduleClassifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ScheduleCreateTest {

    @InjectMocks
    ScheduleCreateService svc;

    @Mock
    ScheduleRepositoryPort out; // 이름 유지
    @Mock
    RepeatScheduleFactory repeatCreate;
    @Mock
    ScheduleClassifier classifier;
    @Mock
    ScheduleGuard scheduleGuard;
    @Mock
    AttachBinder attach;
    @Mock
    NotificationInterfaces notificationInterfaces;
    @Mock
    DomainEventPublisher domainEventPublisher;

    @Test
    @DisplayName("일정 정상 생성")
    public void createScheduleSuccessTest() {
        try (MockedStatic<SecurityUtil> mocked = Mockito.mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(100L);
            mocked.when(SecurityUtil::currentUserName).thenReturn("userA");

            LocalDateTime start = t(2025,8,20,9,0);
            LocalDateTime end = t(2025,8,20,10,0);

            SchedulesModel req = SchedulesModel.builder()
                    .contents("회의")
                    .startTime(start)
                    .endTime(end)
                    .isAllDay(false)
                    .repeatType(RepeatType.NONE).build();

            when(classifier.classify(any())).thenReturn(ScheduleType.SINGLE_DAY);
            when(out.findOverlappingSchedulesInRange(any(), any(), any())).thenReturn(0L); // 겹침 검증 통과 수록

            SchedulesModel saved = req.toBuilder()
                    .id(999L)
                    .memberId(100L)
                    .scheduleType(ScheduleType.SINGLE_DAY)
                    .build();

            when(out.saveAll(anyList())).thenReturn(List.of(saved));

            // when
            SchedulesModel result = svc.saveSchedule(req);

            // then
            assertThat(result.getId()).isEqualTo(999L);
            verify(out).saveAll(anyList());

            // 핵심: 직접 outbox 찌르던 테스트를 스프링 이벤트 발행 위임 검증으로 변경
            verify(domainEventPublisher, times(1)).publish(anyList(), eq(ScheduleActionType.SCHEDULE_CREATED));
        }
    }

    @Test
    @DisplayName("일정 생성 실패-인증이 안된경우")
    public void createSchedule_fail_notAuthorization(){
        try (MockedStatic<SecurityUtil> mocked = Mockito.mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenThrow(new AccessDeniedException("인증 필요"));
            SchedulesModel req = baseSingle(null, "회의", t(2025,8,20,9,0), t(2025,8,20,10,0));

            assertThatThrownBy(() -> svc.saveSchedule(req))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
    }

    @Test
    @DisplayName("일정 생성 성공-반복일정 생성")
    public void createSchedule_success_generateRepeatSchedule(){
        try (MockedStatic<SecurityUtil> mocked = Mockito.mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(100L);
            mocked.when(SecurityUtil::currentUserName).thenReturn("userA");

            SchedulesModel base = baseSingle(100L, "스터디", t(2025,8,20,20,0), t(2025,8,20,21,0))
                    .toBuilder().repeatType(RepeatType.WEEKLY).repeatCount(3).repeatInterval(1).build();

            List<SchedulesModel> generated = List.of(
                    base.toBuilder().repeatGroupId("G").build(),
                    base.toBuilder().repeatGroupId("G").build(),
                    base.toBuilder().repeatGroupId("G").build()
            );

            when(repeatCreate.generateRepeatedSchedules(base)).thenReturn(generated);
            when(classifier.classify(any())).thenReturn(ScheduleType.SINGLE_DAY);
            when(out.findOverlappingSchedulesInRange(any(), any(), any())).thenReturn(0L);

            when(out.saveAll(anyList())).thenAnswer(inv -> {
                List<SchedulesModel> list = inv.getArgument(0);
                return List.of(list.get(0).toBuilder().id(1L).build());
            });

            // when
            SchedulesModel firstSaved = svc.saveSchedule(base);

            // then
            assertThat(firstSaved.getId()).isEqualTo(1L);
            verify(out, times(1)).saveAll(anyList()); // save -> saveAll 일괄 저장 변경 트래킹
            verify(domainEventPublisher, times(1)).publish(anyList(), eq(ScheduleActionType.SCHEDULE_CREATED));
        }
    }

    private static LocalDateTime t(int y, int M, int d, int h, int m) {
        return LocalDateTime.of(y, M, d, h, m);
    }

    private static SchedulesModel baseSingle(Long userId, String contents, LocalDateTime start, LocalDateTime end) {
        return SchedulesModel.builder().memberId(userId).contents(contents).startTime(start).endTime(end).isAllDay(false).repeatType(RepeatType.NONE).build();
    }
}
