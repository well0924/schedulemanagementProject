package com.example.rdbrepository;

import com.example.jpa.config.base.BaseEntity;
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
                @Index(name = "idx_schedules_user_time", columnList = "userId, startTime, endTime")
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
}
