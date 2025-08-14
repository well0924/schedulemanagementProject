package com.example.service.schedule.support;

import com.example.enumerate.schedules.ScheduleType;
import com.example.model.schedules.SchedulesModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class ScheduleClassifier {

    // 주어진 모델의 시작/종료 시각과 ALL_DAY 플래그로 ScheduleType를 판정한다.
    public ScheduleType classify(SchedulesModel m) {
        LocalDateTime s = m.getStartTime();
        LocalDateTime e = m.getEndTime();

        // 방어: 상위 레이어에서 null 방지하도록 권장. 여기서는 보수적으로 SINGLE_DAY 처리.
        if (s == null || e == null) return ScheduleType.SINGLE_DAY;

        if (isSingleDay(s, e)) {
            // ALL_DAY 플래그 또는 시간 범위가 하루 경계면 ALL_DAY로 취급
            return (m.isAllDay() || looksAllDayRange(s, e))
                    ? ScheduleType.ALL_DAY
                    : ScheduleType.SINGLE_DAY;
        }
        return ScheduleType.MULTI_DAY;
    }

    // 단일인지를 판단
    public boolean isSingleDay(LocalDateTime s, LocalDateTime e) {
        return s.toLocalDate().equals(e.toLocalDate());
    }

    // 하루종일의 범위를 판단
    public boolean looksAllDayRange(LocalDateTime s, LocalDateTime e) {
        if (!isSingleDay(s, e)) return false;

        boolean startAtBod = s.toLocalTime().equals(LocalTime.MIDNIGHT);
        boolean endAtEod =
                e.toLocalTime().equals(LocalTime.of(23, 59, 59, 999_000_000)) // ms 정밀 DB
                        || e.toLocalTime().equals(LocalTime.MAX);

        return startAtBod && endAtEod;
    }

    // 시작일
    public LocalDateTime startOfDay(LocalDate d) {
        return d.atStartOfDay();
    }

    // 종료일
    public LocalDateTime endOfDay(LocalDate d) {
        return LocalDateTime.of(d, LocalTime.of(23, 59, 59, 999_000_000));
    }

    //ALL_DAY 정규화 후 타입까지 재판정하여 모델에 반영
    public SchedulesModel normalizeAndClassify(SchedulesModel m) {
        SchedulesModel normalized = normalizeAllDay(m);
        return normalized.toBuilder()
                .scheduleType(classify(normalized))
                .build();
    }

    // ALL_DAY=true 인 모델의 시작/종료 시간을 하루 경계로 정규화
    public SchedulesModel normalizeAllDay(SchedulesModel m) {
        if (!m.isAllDay()) return m;

        LocalDate sDate = m.getStartTime().toLocalDate();
        // start/end 가 다르면 start 기준으로 강제 정렬
        return m.toBuilder()
                .startTime(startOfDay(sDate))
                .endTime(endOfDay(sDate))
                .build();
    }
}
