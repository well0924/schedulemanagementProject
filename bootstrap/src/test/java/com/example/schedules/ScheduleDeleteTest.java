package com.example.schedules;

import com.example.enumerate.schedules.DeleteType;
import com.example.events.enums.ScheduleActionType;
import com.example.inbound.schedules.ScheduleRepositoryPort;
import com.example.interfaces.notification.notification.NotificationInterfaces;
import com.example.model.schedules.SchedulesModel;
import com.example.security.config.SecurityUtil;
import com.example.service.schedule.domainService.ScheduleDeleteService;
import com.example.service.schedule.domainService.guard.ScheduleGuard;
import com.example.service.schedule.domainService.repeat.delete.RepeatDeleteRegistry;
import com.example.service.schedule.domainService.support.DomainEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ScheduleDeleteTest {

    @InjectMocks
    ScheduleDeleteService scheduleDeleteService;

    @Mock
    ScheduleRepositoryPort scheduleRepositoryPort;
    @Mock
    ScheduleGuard guard;
    @Mock
    RepeatDeleteRegistry repeatDelete;
    @Mock
    NotificationInterfaces notificationInterfaces;
    @Mock
    DomainEventPublisher domainEventPublisher;

    @Test
    @DisplayName("일정 단일 삭제 성공")
    public void deleteSchedule_success_Single(){
        try (MockedStatic<SecurityUtil> mocked = Mockito.mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(100L);
            LocalDateTime day = LocalDateTime.of(2025,8,30,0,0);
            SchedulesModel target = SchedulesModel.builder().id(10L).startTime(day).endTime(day).memberId(100L).build();

            when(scheduleRepositoryPort.findById(10L)).thenReturn(target);
            when(repeatDelete.dispatch(eq(DeleteType.SINGLE), eq(target))).thenReturn(List.of(target));

            // when
            scheduleDeleteService.deleteSchedule(10L, DeleteType.SINGLE);

            // then
            verify(repeatDelete).dispatch(DeleteType.SINGLE, target);
            verify(domainEventPublisher, times(1)).publish(anyList(), eq(ScheduleActionType.SCHEDULE_DELETE));
        }
    }

    @Test
    @DisplayName("일정 전체 삭제 성공")
    public void deleteSchedule_success_ALL_REPEAT(){
        try (MockedStatic<SecurityUtil> mocked = Mockito.mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(200L);
            LocalDateTime day = LocalDateTime.of(2025,8,30,0,0);
            SchedulesModel target = SchedulesModel
                    .builder()
                    .id(20L)
                    .memberId(200L)
                    .startTime(day)
                    .repeatGroupId("RG")
                    .build();

            when(scheduleRepositoryPort.findById(20L)).thenReturn(target);
            when(repeatDelete.dispatch(eq(DeleteType.ALL_REPEAT), eq(target))).thenReturn(List.of(target));

            // when
            scheduleDeleteService.deleteSchedule(20L, DeleteType.ALL_REPEAT);

            // then
            verify(repeatDelete).dispatch(DeleteType.ALL_REPEAT, target);
            verify(domainEventPublisher, times(1)).publish(anyList(), eq(ScheduleActionType.SCHEDULE_DELETE));
        }
    }
}
