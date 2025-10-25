package com.example.attach.mapper;

import com.example.apimodel.attach.AttachApiModel;
import com.example.model.attach.AttachModel;
import com.example.rdb.Attach;
import org.springframework.stereotype.Component;

@Component
public class AttachModelMapper {

    public AttachApiModel.AttachResponse toApiModel(AttachModel attachModel) {
        return AttachApiModel.AttachResponse.builder()
                .id(attachModel.getId())
                .originFileName(attachModel.getOriginFileName())
                .storedFileName(attachModel.getStoredFileName())
                .thumbnailFilePath(attachModel.getThumbnailFilePath())
                .fileSize(attachModel.getFileSize())
                .filePath(attachModel.getFilePath())
                .isDeletedAttach(attachModel.isDeletedAttached())
                .build();
    }
}
