package com.example.model.schedules;

import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulesModel {

    private Long id;
    private String contents;
    private int scheduleDays;
    private int scheduleMonth;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long userId; //회원 번호
    private long categoryId; // 카테고리 번호
    private PROGRESS_STATUS progressStatus;
    private boolean isDeletedScheduled;
    private List<String>attachThumbNailImagePath;//섬네일 이미지 경로
    private List<Long> attachIds;//첨부파일 번호
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    public SchedulesModel(Long id,
                          String contents,
                          int scheduleDays,
                          int scheduleMonth,
                          LocalDateTime startTime,
                          LocalDateTime endTime,
                          Long userId,
                          Long categoryId,
                          String progressStatus,
                          String createdBy,
                          String updatedBy,
                          LocalDateTime createdTime,
                          LocalDateTime updatedTime,
                          List<String> attachThumbNailImagePath,
                          List<Long> attachIds) {
        this.id = id;
        this.contents = contents;
        this.scheduleMonth = scheduleMonth;
        this.scheduleDays = scheduleDays;
        this.startTime = startTime;
        this.endTime = endTime;
        this.userId = userId;
        this.categoryId = categoryId;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdTime = createdTime;
        this.updatedTime = updatedTime;
        this.progressStatus = progressStatus != null ? PROGRESS_STATUS.valueOf(progressStatus) : PROGRESS_STATUS.IN_COMPLETE;;
        this.attachThumbNailImagePath = attachThumbNailImagePath;
        this.attachIds = attachIds;
    }

    //진행상태 변경
    public void updateProgressStatus() {

        LocalDateTime now = LocalDateTime.now();

        if (this.startTime == null || this.endTime == null) {
            throw new ScheduleCustomException(ScheduleErrorCode.NOT_START_TIME_AND_END_TIME);
        }

        if (now.isBefore(this.startTime)) {
            this.progressStatus = PROGRESS_STATUS.IN_COMPLETE; // 일정 시작 전
        } else if (now.isAfter(this.endTime)) {
            this.progressStatus = PROGRESS_STATUS.COMPLETE; // 일정 종료됨
        } else {
            this.progressStatus = PROGRESS_STATUS.PROGRESS; // 일정 진행 중
        }
    }

    //진행상태 수정
    public void updateSchedule(LocalDateTime newStartTime, LocalDateTime newEndTime) {

        if (this.progressStatus == PROGRESS_STATUS.COMPLETE) {
            throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_COMPLETED);
        }
        if (newStartTime != null && newEndTime != null && newStartTime.isAfter(newEndTime)) {
            throw new ScheduleCustomException(ScheduleErrorCode.START_TIME_AFTER_END_TIME_EXCEPTION);
        }
        this.startTime = newStartTime;
        this.endTime = newEndTime;
        updateProgressStatus();  // 상태 업데이트
    }

    //진행완료변경
    public void markAsComplete() {
        if (this.progressStatus == PROGRESS_STATUS.COMPLETE) {
            throw new ScheduleCustomException(ScheduleErrorCode.SCHEDULE_COMPLETED);
        }

        this.progressStatus = PROGRESS_STATUS.COMPLETE;
        this.endTime = LocalDateTime.now();
    }

}
