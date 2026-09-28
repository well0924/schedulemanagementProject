package com.example.schedules;

import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.enumerate.schedules.RepeatType;
import com.example.enumerate.schedules.RepeatUpdateType;
import com.example.events.enums.ScheduleActionType;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.interfaces.notification.notification.NotificationInterfaces;
import com.example.model.schedules.SchedulesModel;
import com.example.security.config.SecurityUtil;
import com.example.service.schedule.domainService.ScheduleUpdateService;
import com.example.service.schedule.domainService.guard.ScheduleGuard;
import com.example.service.schedule.domainService.repeat.update.RepeatUpdateRegistry;
import com.example.service.schedule.domainService.support.DomainEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ScheduleUpdateTest {

    @InjectMocks
    ScheduleUpdateService svc;

    @Mock
    ScheduleRepositoryPort scheduleRepositoryPort;
    @Mock
    ScheduleGuard guard;
    @Mock
    RepeatUpdateRegistry repeatUpdate;
    @Mock
    NotificationInterfaces notificationInterfaces;
    @Mock
    DomainEventPublisher domainEventPublisher;

    @Test
    @DisplayName("일정 단일 수정 성공")
    public void updateSchedule_success_SINGLE(){
        try (MockedStatic<SecurityUtil> mocked = Mockito.mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(100L);

            SchedulesModel existing = SchedulesModel.builder()
                    .id(1L)
                    .memberId(100L)
                    .contents("old")
                    .startTime(LocalDateTime.of(2025,8,22,9,0))
                    .endTime(LocalDateTime.of(2025,8,22,10,0))
                    .repeatType(RepeatType.NONE)
                    .build();

            when(scheduleRepositoryPort.findById(1L)).thenReturn(existing);

            SchedulesModel patch = existing.toBuilder().contents("new").build();
            SchedulesModel updated = existing.toBuilder().contents("new").build();

            when(repeatUpdate.dispatch(eq(RepeatUpdateType.SINGLE), eq(existing), eq(patch)))
                    .thenReturn(List.of(updated));

            // when
            SchedulesModel result = svc.updateSchedule(1L, patch, RepeatUpdateType.SINGLE);

            // then
            assertThat(result.getContents()).isEqualTo("new");
            verify(repeatUpdate).dispatch(RepeatUpdateType.SINGLE, existing, patch);

            // 아웃박스 직접 insert 검증 대신, 스프링 이벤트가 아웃바운드로 잘 날아갔는지 토스 여부 확인
            verify(domainEventPublisher, times(1)).publish(anyList(), eq(ScheduleActionType.SCHEDULE_UPDATE));
        }
    }

    @Test
    @DisplayName("반복일정 일부 수정 성공")
    public void updateSchedule_success_AFTER_THIS(){
        SchedulesModel existing = SchedulesModel.builder()
                .id(2L)
                .memberId(9L)
                .repeatGroupId("G")
                .startTime(LocalDateTime.of(2025,8,20,9,0))
                .endTime(LocalDateTime.of(2025,8,20,10,0))
                .repeatType(RepeatType.WEEKLY)
                .build();

        when(scheduleRepositoryPort.findById(2L)).thenReturn(existing);

        SchedulesModel patch = existing.toBuilder().contents("p").build();
        SchedulesModel updated = existing.toBuilder().contents("p").build();

        when(repeatUpdate.dispatch(eq(RepeatUpdateType.AFTER_THIS), eq(existing), eq(patch)))
                .thenReturn(List.of(updated));

        // when
        SchedulesModel result = svc.updateSchedule(2L, patch, RepeatUpdateType.AFTER_THIS);

        // then
        assertThat(result.getContents()).isEqualTo("p");
        verify(repeatUpdate).dispatch(RepeatUpdateType.AFTER_THIS, existing, patch);
        verify(domainEventPublisher, times(1)).publish(anyList(), eq(ScheduleActionType.SCHEDULE_UPDATE));
    }

    @Test
    @DisplayName("일정 상태 변경 성공 - 소유자")
    public void updateProgressStatus_success_owner(){
        SchedulesModel existing = SchedulesModel.builder().id(3L).memberId(100L).build();
        when(scheduleRepositoryPort.findById(3L)).thenReturn(existing);

        PROGRESS_STATUS result = svc.updateProgressStatus(3L, PROGRESS_STATUS.COMPLETE);

        assertThat(result).isEqualTo(PROGRESS_STATUS.COMPLETE);
        verify(guard).assertOwnerOrAdmin(existing);
        verify(scheduleRepositoryPort).updateStatusOnly(3L, PROGRESS_STATUS.COMPLETE);
    }

    @Test
    @DisplayName("일정 상태 변경 실패 - 소유자가 아니면 변경하지 않는다")
    public void updateProgressStatus_fail_notOwner(){
        SchedulesModel existing = SchedulesModel.builder().id(4L).memberId(100L).build();
        when(scheduleRepositoryPort.findById(4L)).thenReturn(existing);
        doThrow(new ScheduleCustomException(ScheduleErrorCode.NOT_SCHEDULE_OWNER))
                .when(guard).assertOwnerOrAdmin(existing);

        assertThatThrownBy(() -> svc.updateProgressStatus(4L, PROGRESS_STATUS.COMPLETE))
                .isInstanceOf(ScheduleCustomException.class);

        verify(scheduleRepositoryPort, never()).updateStatusOnly(anyLong(), any());
    }

    @Test
    @DisplayName("일정 상태 변경 실패 - 없는 일정이면 변경하지 않는다")
    public void updateProgressStatus_fail_notFound(){
        when(scheduleRepositoryPort.findById(999L))
                .thenThrow(new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));

        assertThatThrownBy(() -> svc.updateProgressStatus(999L, PROGRESS_STATUS.COMPLETE))
                .isInstanceOf(ScheduleCustomException.class);

        verify(scheduleRepositoryPort, never()).updateStatusOnly(anyLong(), any());
    }
}
