package com.example.apimodel.schedule;

import com.example.apimodel.attach.AttachApiModel;
import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.enumerate.schedules.RepeatType;
import com.example.enumerate.schedules.ScheduleType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public class ScheduleApiModel {

    @Builder
    public record requestSchedule(
            @NotBlank(message = "일정 내용은 필수입니다.")
            String contents,

            @NotNull(message = "일정 일자는 필수입니다.")
            @Min(value = 1, message = "일정 일자는 1일 이상이어야 합니다.")
            @Max(value = 31, message = "일정 일자는 31일 이하이어야 합니다.")
            Integer scheduleDays,

            @NotNull(message = "일정 월은 필수입니다.")
            @Min(value = 1, message = "월은 1~12 사이여야 합니다.")
            @Max(value = 12, message = "월은 1~12 사이여야 합니다.")
            Integer scheduleMonth,

            @NotNull(message = "시작 시간은 필수입니다.")
            LocalDateTime startTime,

            @NotNull(message = "종료 시간은 필수입니다.")
            LocalDateTime endTime,

            @NotNull(message = "회원 ID는 필수입니다.")
            Long userId,

            @NotNull(message = "카테고리 ID는 필수입니다.")
            Long categoryId,

            // 첨부파일은 옵션. 검증 안 걸고 서비스단에서 null/empty 체크
            List<Long> attachIds,

            // 반복 옵션은 선택적
            RepeatType repeatType,
            //일정 유형
            ScheduleType  scheduleType,
            
            // 반복 횟수는 0 이상만 허용 (기본 0)
            @Min(value = 0, message = "반복 횟수는 0 이상이어야 합니다.")
            Integer repeatCount,

            @Min(value = 0, message = "반복 간격은 0 이상이어야 합니다.")
            Integer repeatInterval,

            @NotNull(message = "일정 유형은 필수입니다.")
            Boolean isAllDay
    ) {

    }

    @Builder
    public record updateSchedule(
            @NotNull(message = "일정 ID는 필수입니다.")
            Long scheduleId,

            @Size(max = 255, message = "일정 내용은 255자 이내여야 합니다.")
            String contents,

            @Min(value = 1, message = "일정 일자는 1일 이상이어야 합니다.")
            @Max(value = 31, message = "일정 일자는 31일 이하이어야 합니다.")
            Integer scheduleDays,

            @Min(value = 1, message = "월은 1~12 사이여야 합니다.")
            @Max(value = 12, message = "월은 1~12 사이여야 합니다.")
            Integer scheduleMonth,

            LocalDateTime startTime,

            LocalDateTime endTime,

            // 진행 상태 (null 허용 → 그대로 두면 유지)
            PROGRESS_STATUS progressStatus,

            @NotNull(message = "카테고리 ID는 필수입니다.")
            Long categoryId,

            @NotNull(message = "회원 ID는 필수입니다.")
            Long userId,

            RepeatType repeatType,

            ScheduleType scheduleType,

            @Min(value = 0, message = "반복 횟수는 0 이상이어야 합니다.")
            Integer repeatCount,

            @Min(value = 0, message = "반복 간격은 0 이상이어야 합니다.")
            Integer repeatInterval,

            Boolean isAllDay,
            // 첨부파일은 옵션. 검증 안 걸고 서비스단에서 null/empty 체크
            @JsonProperty("attachIds")
            List<Long> attachIds
    ) {

    }

    @Builder
    public record responseSchedule(
            long id, // 일정 ID
            String contents, // 일정 내용
            Integer scheduleDays, // 일정 날짜
            Integer scheduleMonth, // 일정 월
            LocalDateTime startTime, // 시작 시간
            LocalDateTime endTime, // 종료 시간
            long userId, // 회원 ID
            long categoryId, // 카테고리 ID
            PROGRESS_STATUS progressStatus, // 진행 상태
            boolean isDeletedScheduled, // 삭제 여부
            RepeatType repeatType, // 일정 반복 유형
            int repeatCount, // 일정 반복 횟수
            int repeatInterval,
            boolean isAllDay,
            ScheduleType scheduleType,
            String repeatGroupId, //일정 반복 groupId
            String createdBy, // 생성자
            LocalDateTime createdTime, // 생성 시간
            String updatedBy, // 수정자
            LocalDateTime updatedTime, // 수정 시간
            List<AttachApiModel.AttachResponse> attachFiles //첨부파일
    ){}

    @Builder
    public record responseScheduleStatus(
            long id,
            String progressStatus
    ){}
}
