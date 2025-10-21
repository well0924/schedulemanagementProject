package com.example.schedules;

import com.example.enumerate.schedules.DeleteType;
import com.example.enumerate.schedules.RepeatType;
import com.example.enumerate.schedules.RepeatUpdateType;
import com.example.enumerate.schedules.ScheduleType;
import com.example.events.enums.NotificationChannel;
import com.example.events.enums.ScheduleActionType;
import com.example.model.schedules.SchedulesModel;
import com.example.outbound.schedule.ScheduleOutConnector;
import com.example.security.config.SecurityUtil;
import com.example.service.schedule.ScheduleDomainService;
import com.example.service.schedule.guard.ScheduleGuard;
import com.example.service.schedule.repeat.create.RepeatScheduleFactory;
import com.example.service.schedule.repeat.delete.RepeatDeleteRegistry;
import com.example.service.schedule.repeat.update.RepeatUpdateRegistry;
import com.example.service.schedule.support.AttachBinder;
import com.example.service.schedule.support.DomainEventPublisher;
import com.example.service.schedule.support.ScheduleClassifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = { ScheduleDomainService.class })
public class ScheduleCRUDTest {

    @Autowired
    ScheduleDomainService svc;
    @MockBean
    ScheduleOutConnector out;
    @MockBean
    DomainEventPublisher events;
    @MockBean
    AttachBinder attach;
    @MockBean
    ScheduleGuard guard;
    @MockBean
    ScheduleClassifier classifier;
    @MockBean
    RepeatUpdateRegistry repeatUpdate;
    @MockBean
    RepeatDeleteRegistry repeatDelete;
    @MockBean
    RepeatScheduleFactory repeatCreate;

    @Test
    @DisplayName("일정 정상 생성")
    public void createScheduleSuccessTest() {
        try (MockedStatic<SecurityUtil> mocked = Mockito.mockStatic(SecurityUtil.class)) {
            // given
            mocked.when(SecurityUtil::currentUserId).thenReturn(100L);
            mocked.when(SecurityUtil::currentUserName).thenReturn("userA");

            LocalDateTime start = LocalDateTime.of(2025, 8, 20, 9, 0);
            LocalDateTime end = LocalDateTime.of(2025, 8, 20, 10, 0);

            SchedulesModel req = SchedulesModel
                    .builder()
                    .contents("회의")
                    .startTime(start)
                    .endTime(end)
                    .isAllDay(false)
                    .repeatType(RepeatType.NONE) .build();
            when(classifier.classify(any())).thenReturn(ScheduleType.SINGLE_DAY);
            when(attach.hasAttachFiles(req)).thenReturn(false);

            // save 전 검증 호출만 하고
            doNothing().when(out).validateScheduleConflict(any());
            // save 결과 리턴
            SchedulesModel saved = req
                    .toBuilder()
                    .id(999L)
                    .memberId(100L)
                    .scheduleType(ScheduleType.SINGLE_DAY)
                    .build();

            when(out.saveSchedule(any())).thenReturn(saved);
            // when
            SchedulesModel result = svc.saveSchedule(req);

            // then
            assertThat(result.getId()).isEqualTo(999L);
            assertThat(result.getMemberId()).isEqualTo(100L);
            assertThat(result.getScheduleType()).isEqualTo(ScheduleType.SINGLE_DAY);
            verify(out).validateScheduleConflict(any(SchedulesModel.class));
            verify(out).saveSchedule(any(SchedulesModel.class));
            verify(events).publishScheduleEvent(saved, ScheduleActionType.SCHEDULE_CREATED, NotificationChannel.WEB);
            verify(attach, never()).bindToSchedule(anyList(), anyLong()); }
    }

    @Test
    @DisplayName("일정 생성 실패-인증이 안된경우")
    public void createSchedule_fail_notAuthorization(){
        try (MockedStatic<SecurityUtil> mocked = Mockito.mockStatic(SecurityUtil.class)) {
            // SecurityUtil이 인증 예외를 던지도록
            mocked.when(SecurityUtil::currentUserId)
                    .thenThrow(new AccessDeniedException("인증 필요"));
            SchedulesModel req = baseSingle(null, "회의", t(2025,8,20,9,0), t(2025,8,20,10,0));

            assertThatThrownBy(() -> svc.saveSchedule(req))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                    .hasMessageContaining("인증"); }
    }

    @Test
    @DisplayName("일정 생성 성공-반복일정 생성")
    public void createSchedule_success_generateRepeatSchedule(){
        try (MockedStatic<SecurityUtil> mocked = Mockito.mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(100L);
            mocked.when(SecurityUtil::currentUserName).thenReturn("userA");
            // 요청: 주간 반복 3회
            SchedulesModel base = baseSingle(100L, "스터디", t(2025,8,20,20,0), t(2025,8,20,21,0))
                    .toBuilder()
                    .repeatType(RepeatType.WEEKLY)
                    .repeatCount(3)
                    .repeatInterval(1)
                    .build();
            // 팩토리가 3개 인스턴스 생성해서 준다고 가정
            List<SchedulesModel> generated = List
                    .of( base.toBuilder().repeatGroupId("G").build(),
                         base.toBuilder().repeatGroupId("G").build(),
                         base.toBuilder().repeatGroupId("G").build() );

            when(repeatCreate.generateRepeatedSchedules(base)).thenReturn(generated);

            // 분류 + 충돌검사 + 저장 스텁
            when(classifier.classify(any())).thenReturn(ScheduleType.SINGLE_DAY);
            doNothing().when(out).validateScheduleConflict(any());

            // saveSchedule 이 호출될 때마다 id 증가해서 리턴
            final long[] idSeq = new long[]{1};
            when(out.saveSchedule(any()))
                    .thenAnswer(inv -> { SchedulesModel arg = inv.getArgument(0);
            return arg
                    .toBuilder()
                    .id(idSeq[0]++)
                    .memberId(100L)
                    .scheduleType(ScheduleType.SINGLE_DAY)
                    .build();
                    });
            when(attach.hasAttachFiles(any())).thenReturn(false);
            // when
            SchedulesModel firstSaved = svc.saveSchedule(base);
            // then
            assertThat(firstSaved.getId()).isEqualTo(1L);
            assertThat(firstSaved.getMemberId()).isEqualTo(100L);
            // 3건 저장 호출 확인
            verify(out, times(3)).saveSchedule(any(SchedulesModel.class));
            // 이벤트 1회(최초 스케줄 기준) 발행 확인
            verify(events).publishScheduleEvent(firstSaved, ScheduleActionType.SCHEDULE_CREATED,NotificationChannel.WEB);
        }
    }

    @Test
    @DisplayName("일정 생성 성공-하루종일 일정 설정")
    public void createSchedule_success_isAllDays(){
        try (MockedStatic<SecurityUtil> mocked = Mockito.mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(777L);
            LocalDateTime day = t(2025,8,30,0,0);
            SchedulesModel req = SchedulesModel.builder()
                    .contents("휴가")
                    .startTime(day)
                    .endTime(day)
                    // 같은 날
                    .isAllDay(true)
                    .repeatType(RepeatType.NONE)
                    .build();

            when(classifier.classify(any())).thenReturn(ScheduleType.ALL_DAY);
            doNothing().when(out).validateScheduleConflict(any());
            when(out.saveSchedule(any())).thenAnswer(inv -> ((SchedulesModel)inv.getArgument(0))
                    .toBuilder().id(500L).memberId(777L).scheduleType(ScheduleType.ALL_DAY).build());

            when(attach.hasAttachFiles(req)).thenReturn(false);

            SchedulesModel saved = svc.saveSchedule(req);

            assertThat(saved.getId()).isEqualTo(500L);
            assertThat(saved.getScheduleType()).isEqualTo(ScheduleType.ALL_DAY);
            assertThat(saved.getMemberId()).isEqualTo(777L);

            verify(events).publishScheduleEvent(saved, ScheduleActionType.SCHEDULE_CREATED,NotificationChannel.WEB);
        }
    }

    @Test
    @DisplayName("일정 단일 삭제 성공")
    public void deleteSchedule_success_Single(){
        try (MockedStatic<SecurityUtil> mocked = Mockito.mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(100L);
            SchedulesModel target = SchedulesModel
                    .builder().id(10L).memberId(100L).build();

            when(out.findById(10L)).thenReturn(target);
            doNothing().when(guard).assertOwnerOrAdmin(target);

            // 레지스트리는 SINGLE일 때 내부에서 out.deleteSchedule을 호출하도록 시뮬레이션
            doAnswer(inv -> { out.deleteSchedule(10L); return null; })
                    .when(repeatDelete).dispatch(eq(DeleteType.SINGLE), eq(target));
            // when
            svc.deleteSchedule(10L, DeleteType.SINGLE);
            // then
            verify(repeatDelete).dispatch(DeleteType.SINGLE, target);
            verify(out).deleteSchedule(10L);
            verify(events).publishScheduleEvent(target, ScheduleActionType.SCHEDULE_DELETE,NotificationChannel.WEB);
        }
    }

    @Test
    @DisplayName("일정 전체 삭제 성공")
    public void deleteSchedule_success_ALL_REPEAT(){
        try (MockedStatic<SecurityUtil> mocked = Mockito.mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(200L);
            // given
            SchedulesModel target = SchedulesModel
                    .builder()
                    .id(20L)
                    .memberId(200L)
                    .repeatGroupId("RG")
                    .build();

            when(out.findById(20L)).thenReturn(target);
            doNothing().when(guard).assertOwnerOrAdmin(target);

            // 레지스트리가 내부에서 groupId 기준 일괄 삭제를 트리거한다고 가정
            doAnswer(inv -> { out.markAsDeletedByRepeatGroupId("RG"); return null; })
                    .when(repeatDelete).dispatch(eq(DeleteType.ALL_REPEAT), eq(target));

            // when
            svc.deleteSchedule(20L, DeleteType.ALL_REPEAT);

            // then
            verify(repeatDelete).dispatch(DeleteType.ALL_REPEAT, target);
            verify(out).markAsDeletedByRepeatGroupId("RG");
            verify(events).publishScheduleEvent(target, ScheduleActionType.SCHEDULE_DELETE,NotificationChannel.WEB);
        }
    }

    @Test
    @DisplayName("일정 일부 삭제 성공")
    public void deleteSchedule_success_AFTER_THIS(){
        try (MockedStatic<SecurityUtil> mocked = Mockito.mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(300L);
            LocalDateTime st = t(2025,9,1,9,0);
            SchedulesModel target = SchedulesModel
                    .builder()
                    .id(30L)
                    .memberId(300L)
                    .repeatGroupId("GRP")
                    .startTime(st)
                    .build();

            when(out.findById(30L)).thenReturn(target);
            doNothing().when(guard).assertOwnerOrAdmin(target);
            doAnswer(inv -> { out.markAsDeletedAfter("GRP", st); return null; })
                    .when(repeatDelete).dispatch(eq(DeleteType.AFTER_THIS), eq(target));

            svc.deleteSchedule(30L, DeleteType.AFTER_THIS);

            verify(repeatDelete).dispatch(DeleteType.AFTER_THIS, target);
            verify(out).markAsDeletedAfter("GRP", st);
            verify(events).publishScheduleEvent(target, ScheduleActionType.SCHEDULE_DELETE,NotificationChannel.WEB);
        }
    }

    @Test
    @DisplayName("일정 단일 수정 성공")
    public void updateSchedule_success_SINGLE(){
        try (MockedStatic<SecurityUtil> mocked = Mockito.mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(100L);
            SchedulesModel existing = SchedulesModel
                    .builder()
                    .id(1L)
                    .memberId(100L)
                    .contents("old")
                    .startTime(t(2025,8,22,9,0))
                    .endTime(t(2025,8,22,10,0))
                    .repeatType(RepeatType.NONE)
                    .build();

            when(out.findById(1L)).thenReturn(existing);
            doNothing().when(out).validateScheduleConflict(existing);
            doNothing().when(guard).assertOwnerOrAdmin(existing);

            SchedulesModel patch = existing.toBuilder().contents("new").build();
            SchedulesModel updated = existing.toBuilder().contents("new").build();

            when(repeatUpdate.dispatch(eq(RepeatUpdateType.SINGLE), eq(existing), eq(patch)))
                    .thenReturn(updated);

            SchedulesModel result = svc.updateSchedule(1L, patch, RepeatUpdateType.SINGLE);

            assertThat(result.getContents()).isEqualTo("new");
            verify(repeatUpdate).dispatch(RepeatUpdateType.SINGLE, existing, patch);
        }
    }

    @Test
    @DisplayName("반복일정 일부 수정 성공")
    public void updateSchedule_success_AFTER_THIS(){
        SchedulesModel existing = SchedulesModel
                .builder()
                .id(2L)
                .memberId(9L)
                .repeatGroupId("G")
                .startTime(t(2025,8,20,9,0))
                .endTime(t(2025,8,20,10,0))
                .repeatType(RepeatType.WEEKLY)
                .build();

        when(out.findById(2L)).thenReturn(existing);
        doNothing().when(out).validateScheduleConflict(existing);
        doNothing().when(guard).assertOwnerOrAdmin(existing);

        SchedulesModel patch = existing.toBuilder().contents("p").build();
        SchedulesModel updated = existing.toBuilder().contents("p").build();

        when(repeatUpdate.dispatch(eq(RepeatUpdateType.AFTER_THIS), eq(existing), eq(patch)))
                .thenReturn(updated);

        SchedulesModel result = svc.updateSchedule(2L, patch, RepeatUpdateType.AFTER_THIS);

        assertThat(result.getContents()).isEqualTo("p");
        verify(repeatUpdate).dispatch(RepeatUpdateType.AFTER_THIS, existing, patch);
    }

    @Test
    @DisplayName("반복일정 전체 수정 성공")
    public void updateSchedule_success_ALL_REPEAT(){
        SchedulesModel existing = SchedulesModel
                .builder()
                .id(3L)
                .memberId(9L)
                .repeatGroupId("G2")
                .startTime(t(2025,8,21,9,0))
                .endTime(t(2025,8,21,10,0))
                .repeatType(RepeatType.WEEKLY)
                .build();

        when(out.findById(3L)).thenReturn(existing);
        doNothing().when(out).validateScheduleConflict(existing);
        doNothing().when(guard).assertOwnerOrAdmin(existing);

        SchedulesModel patch = existing.toBuilder().contents("ALL").build();
        SchedulesModel updated = existing.toBuilder().contents("ALL").build();

        when(repeatUpdate.dispatch(eq(RepeatUpdateType.ALL), eq(existing), eq(patch)))
                .thenReturn(updated);

        SchedulesModel result = svc.updateSchedule(3L, patch, RepeatUpdateType.ALL);

        assertThat(result.getContents()).isEqualTo("ALL");
        verify(repeatUpdate).dispatch(RepeatUpdateType.ALL, existing, patch);
    }

    private static LocalDateTime t(int y, int M, int d, int h, int m) {
        return LocalDateTime.of(y, M, d, h, m);
    }

    private static SchedulesModel baseSingle(Long userId, String contents, LocalDateTime start, LocalDateTime end) {
        return SchedulesModel
                .builder()
                .memberId(userId)
                .contents(contents)
                .startTime(start)
                .endTime(end)
                .isAllDay(false)
                .repeatType(RepeatType.NONE)
                .build();
    }
}
