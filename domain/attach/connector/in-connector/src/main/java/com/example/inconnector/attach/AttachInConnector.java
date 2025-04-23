package com.example.inconnector.attach;

import static com.example.apimodel.attach.AttachApiModel.AttachResponse;
import com.example.interfaces.attach.AttachInterfaces;
import com.example.model.attach.AttachModel;
import com.example.service.attach.AttachService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AttachInConnector implements AttachInterfaces {

    private final AttachService attachService;

    @Override
    public List<AttachResponse> findAll() {
        return attachService.findAll()
                .stream()
                .map(this::toApiModel)
                .collect(Collectors.toList());
    }

    @Override
    public AttachResponse findById(Long attachId) {
        return toApiModel(attachService.findById(attachId));
    }

    @Override
    public AttachResponse findByOriginFileName(String originFileName) {
        return toApiModel(attachService.findByOriginFileName(originFileName));
    }

    @Override
    public List<AttachResponse> findByIds(List<Long> attachIds) {
        return attachService.findByIds(attachIds)
                .stream()
                .map(this::toApiModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> generatePreSignedUrls(List<String> fileNames) {
        return attachService.generatePreSignedUrls(fileNames);
    }

    @Override
    public String generateDownloadPreSignedUrl(String fileName) {
        return attachService.generateDownloadPreSignedUrl(fileName);
    }

    //업로드 + 섬네일 생성
    @Override
    public List<AttachResponse> createdAttach(List<String> uploadedFileNames) throws IOException {
        return attachService.createAttach(uploadedFileNames)
                .stream()
                .map(this::toApiModel)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAttach(Long attachId) {
        attachService.deleteAttachAndFile(attachId);
    }

    @Override
    public void updateScheduleId(List<Long> fileIds, Long scheduleId) {
        attachService.updateScheduleId(fileIds,scheduleId);
    }

    //model <-> api model
    private AttachResponse toApiModel(AttachModel attachModel) {
        return AttachResponse.builder()
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
