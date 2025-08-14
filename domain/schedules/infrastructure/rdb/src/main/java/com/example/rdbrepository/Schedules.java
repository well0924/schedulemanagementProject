package com.example.rdbrepository;

import com.example.jpa.config.base.BaseEntity;
import com.example.model.schedules.SchedulesModel;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Getter
@Entity
@Builder(toBuilder = true)
@Table(
        name = "schedules",
        indexes = {
                // 일정 충돌 인덱스
                @Index(name = "idx_schedules_user_time", columnList = "userId, startTime, endTime"),
                // [반복/그룹 조작] repeat_group_id + user_id (+ start_time >= ?)
                @Index(name = "idx_sched_group_user_start", columnList = "repeat_group_id, user_id, start_time"),
                // [상태별 조회]
                @Index(name = "idx_sched_user_status", columnList = "user_id, progress_status")
        }
)
@NoArgsConstructor
@AllArgsConstructor
public class Schedules extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String contents;

    private Integer scheduleMonth;

    private Integer scheduleDay;

    private Boolean isDeletedScheduled = false;
    //회원의 번호
    private Long userId;
    //카테고리의 번호
    private Long categoryId;
    //일정 상태
    private String progress_status;
    //일정 반복 유형
    private String repeatType;
    //일정 반복 횟수
    private Integer repeatCount;
    //일정 반복 그룹 아이디.(일정 반복 삭제에 사용할 id)
    private String repeatGroupId;
    // 일정 반복 간격 추가
    @Nullable
    private Integer repeatInterval;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
    //일정 타입(기본값은 false,하루종일이면 true)
    private Boolean isAllDay;
    //일정 타입(단일, 하루종일, 장기)
    private String scheduleType;

    public void isDeletedScheduled() {
        this.isDeletedScheduled = true;
    }

    public void updateSchedule(SchedulesModel model) {
        this.contents = model.getContents();
        this.scheduleDay = model.getScheduleDays();
        this.scheduleMonth = model.getScheduleMonth();
        this.progress_status = model.getProgressStatus().name();
        this.startTime = model.getStartTime();
        this.endTime = model.getEndTime();
        this.categoryId = model.getCategoryId();
        this.userId = model.getUserId();
        this.repeatType = model.getRepeatType().name();
        this.repeatCount = model.getRepeatCount();
        this.repeatInterval = model.getRepeatInterval();
        this.scheduleType = model.getScheduleType().name();
        this.isAllDay = model.isAllDay();
    }
}
