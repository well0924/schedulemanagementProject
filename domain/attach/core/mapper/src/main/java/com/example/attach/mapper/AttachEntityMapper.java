package com.example.attach.mapper;

import com.example.model.attach.AttachModel;
import com.example.rdb.Attach;
import org.springframework.stereotype.Component;

@Component
public class AttachEntityMapper {

    public AttachModel toModel(Attach attach) {
        return AttachModel
                .builder()
                .id(attach.getId())
                .fileSize(attach.getFileSize())
                .originFileName(attach.getOriginFileName())
                .storedFileName(attach.getStoredFileName())
                .thumbnailFilePath(attach.getThumbnailFilePath())
                .filePath(attach.getFilePath())
                .scheduledId(attach.getScheduledId())
                .createdBy(attach.getCreatedBy())
                .createdTime(attach.getCreatedTime())
                .updatedBy(attach.getUpdatedBy())
                .updatedTime(attach.getUpdatedTime())
                .build();
    }
}
