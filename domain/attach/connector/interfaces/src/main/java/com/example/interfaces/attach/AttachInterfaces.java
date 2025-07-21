package com.example.interfaces.attach;

import org.springframework.web.multipart.MultipartFile;

import static com.example.apimodel.attach.AttachApiModel.AttachResponse;

import java.io.IOException;
import java.util.List;

public interface AttachInterfaces {
    List<AttachResponse> findAll();
    AttachResponse findById(Long attachId);
    AttachResponse findByOriginFileName(String originFileName);
    List<AttachResponse> createdAttach(List<String> uploadedFileNames) throws IOException;  // 수정됨
    List<AttachResponse> findByIds(List<Long> attachIds);
    void deleteAttach(Long attachId);
    void updateScheduleId(List<Long> fileIds, Long scheduleId);
    List<String> generatePreSignedUrls(List<String> fileNames);  // 업로드용 Presigned URL
    String generateDownloadPreSignedUrl(String fileName);
    List<AttachResponse> uploadDirect(List<MultipartFile>files) throws IOException;
}
