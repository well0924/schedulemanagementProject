package com.example.rdbrepository;

import com.example.enumerate.schedules.PROGRESS_STATUS;
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

    public void isDeletedScheduled() {
        this.isDeletedScheduled = true;
    }

    // Enum을 반환하는 Getter 추가
    public PROGRESS_STATUS getProgressStatusEnum() {
        return PROGRESS_STATUS.fromString(this.progress_status);
    }

    // Enum을 받아 String으로 저장하는 Setter 추가
    public void setProgressStatusEnum(PROGRESS_STATUS progressStatus) {
        this.progress_status = progressStatus.getValue();
    }
}
