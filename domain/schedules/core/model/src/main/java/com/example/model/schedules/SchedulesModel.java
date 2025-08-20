package com.example.model.schedules;

import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.enumerate.schedules.RepeatType;
import com.example.enumerate.schedules.ScheduleType;
import com.example.exception.schedules.dto.ScheduleErrorCode;
import com.example.exception.schedules.exception.ScheduleCustomException;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SchedulesModel {

    private Long id;
    private String contents;
    private Integer scheduleDays;
    private Integer scheduleMonth;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long memberId; //회원 번호
    private Long categoryId; // 카테고리 번호
    private PROGRESS_STATUS progressStatus;
    private boolean isDeletedScheduled;
    private List<String>attachThumbNailImagePath;//섬네일 이미지 경로
    private List<Long> attachIds;//첨부파일 번호
    private RepeatType repeatType;//반복유형
    private Integer repeatCount;//반복횟수
    private Integer repeatInterval;//반복주기.
    private String repeatGroupId;
    private boolean isAllDay;
    private ScheduleType scheduleType;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    public SchedulesModel(Long id,
                          String contents,
                          Integer scheduleDays,
                          Integer scheduleMonth,
                          LocalDateTime startTime,
                          LocalDateTime endTime,
                          Long memberId,
                          Long categoryId,
                          String progressStatus,
                          String repeatType,
                          Integer repeatCount,
                          Integer repeatInterval,
                          String repeatGroupId,
                          String scheduleType,
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
        this.memberId = memberId;
        this.categoryId = categoryId;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdTime = createdTime;
        this.updatedTime = updatedTime;
        this.progressStatus = progressStatus != null ? PROGRESS_STATUS.valueOf(progressStatus) : PROGRESS_STATUS.IN_COMPLETE;;
        this.attachThumbNailImagePath = attachThumbNailImagePath;
        this.attachIds = attachIds;
        this.repeatType = repeatType != null ? RepeatType.valueOf(repeatType) : RepeatType.NONE;
        this.repeatCount = repeatCount;
        this.repeatGroupId = repeatGroupId;
        this.repeatInterval = repeatInterval;
        this.scheduleType = scheduleType != null ? ScheduleType.valueOf(scheduleType) : ScheduleType.SINGLE_DAY;
    }

    //진행상태 업데이트
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

    //일정 반복
    public SchedulesModel shiftScheduleBy(RepeatType rule, int offset) {
        LocalDateTime newStart = this.startTime;
        LocalDateTime newEnd = this.endTime;

        switch (rule) {
            case DAILY -> {
                newStart = newStart.plusDays(offset);
                newEnd = newEnd.plusDays(offset);
            }
            case WEEKLY -> {
                newStart = newStart.plusWeeks(offset);
                newEnd = newEnd.plusWeeks(offset);
            }
            case MONTHLY -> {
                newStart = newStart.plusMonths(offset);
                newEnd = newEnd.plusMonths(offset);
            }
            default -> {
                // NONE이거나 이상하면 자기 자신 그대로 반환
                return this;
            }
        }

        return this.toBuilder()
                .startTime(newStart)
                .endTime(newEnd)
                .build();
    }

}
