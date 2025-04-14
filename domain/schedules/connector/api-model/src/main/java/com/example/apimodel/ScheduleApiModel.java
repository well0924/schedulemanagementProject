package com.example.apimodel;

import com.example.apimodel.attach.AttachApiModel;
import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.enumerate.schedules.RepeatType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public class ScheduleApiModel {

    @Builder
    public record requestSchedule(
            String contents, // 일정 내용
            Integer scheduleDays, // 일정 날짜
            Integer scheduleMonth, // 일정 월
            LocalDateTime startTime, // 시작 시간
            LocalDateTime endTime, // 종료 시간
            Long userId, // 회원 ID
            Long categoryId, // 카테고리 ID
            List<Long> attachIds, //첨부파일 번호(다중파일)
            RepeatType repeatType, //반복 유형
            Integer repeatCount, //일정 반복
            Integer repeatInterval
    ) {

    }

    @Builder
    public record updateSchedule(
            Long scheduleId, //일정 번호
            String contents, // 일정 내용 변경 가능
            Integer scheduleDays, // 일정 날짜 변경 가능
            Integer scheduleMonth, // 일정 월 변경 가능
            LocalDateTime startTime, // 시작 시간 변경 가능
            LocalDateTime endTime, // 종료 시간 변경 가능
            PROGRESS_STATUS progressStatus, // 진행 상태 변경 가능
            Long categoryId, // 카테고리 변경 가능
            Long userId, // 회원 id
            RepeatType repeatType,//반복 유형
            Integer repeatCount, //반복 횟수
            Integer repeatInterval
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
            String repeatGroupId, //일정 반복 groupId
            String createdBy, // 생성자
            LocalDateTime createdTime, // 생성 시간
            String updatedBy, // 수정자
            LocalDateTime updatedTime, // 수정 시간
            List<AttachApiModel.AttachResponse> attachFiles //첨부파일
    ){}
}
