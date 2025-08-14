package com.example.service.schedule.support;

import com.example.inbound.attach.AttachInConnector;
import com.example.model.schedules.SchedulesModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttachBinder {

    private final AttachInConnector attachInConnector;

    public boolean hasAttachFiles(SchedulesModel model) {
        return model.getAttachIds() != null && !model.getAttachIds().isEmpty();
    }

    // 신규 첨부파일 바인딩
    public void bindToSchedule(List<Long> attachIds, Long scheduleId) {
        if (attachIds != null && !attachIds.isEmpty()) {
            attachInConnector.updateScheduleId(attachIds, scheduleId);
        }
    }

    // 일정 수정용 첨부파일 업로드
    public SchedulesModel handleAttachUpdate(SchedulesModel existing, SchedulesModel updated) {
        try {
            List<Long> existingAttachIds = Optional.ofNullable(existing.getAttachIds()).orElse(new ArrayList<>());
            List<Long> newAttachIds = updated.getAttachIds(); // null 허용

            if (newAttachIds != null && !newAttachIds.isEmpty()) {
                List<Long> toDelete = new ArrayList<>(existingAttachIds);
                toDelete.removeAll(newAttachIds);

                for (Long attachId : toDelete) {
                    attachInConnector.deleteAttach(attachId);
                }

                attachInConnector.updateScheduleId(newAttachIds, existing.getId());
                updated = updated.toBuilder().attachIds(newAttachIds).build();
            }
        } catch (Exception e) {
            log.error("첨부파일 처리 중 오류 발생", e);
        }
        return updated;
    }


}
