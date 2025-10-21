package com.example.service.attach;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.example.attach.dto.AttachErrorCode;
import com.example.attach.exception.AttachCustomExceptionHandler;
import com.example.interfaces.attach.AmazonS3Port;
import com.example.interfaces.attach.AttachRepositoryPort;
import com.example.model.attach.AttachModel;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AttachService {

    private final AmazonS3Port amazonS3;

    private final ThumbnailService thumbnailService;

    private final AttachRepositoryPort attachRepository;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    @Value("${server.file.thumbnail-width:200}")
    private int thumbnailWidth;

    @Value("${server.file.thumbnail-height:200}")
    private int thumbnailHeight;

    @Transactional(readOnly = true)
    public List<AttachModel> findAll() {
        return attachRepository.findAll();
    }

    @Transactional(readOnly = true)
    public AttachModel findById(Long attachId) {
        return attachRepository.findById(attachId);
    }

    @Transactional(readOnly = true)
    public List<AttachModel> findByIds(List<Long>attachIds) {
        return attachRepository.findByIdIn(attachIds);
    }

    @Transactional(readOnly = true)
    public AttachModel findByOriginFileName(String originFileName) {
        return attachRepository.findByOriginFileName(originFileName);
    }

    @Transactional(readOnly = true)
    public AttachModel findByStoredFileName(String storedFileName) {
        return attachRepository.findByStoredFileName(storedFileName);
    }

    // S3 presingedurl적용.
    @Transactional(readOnly = true)
    public List<String> generatePreSignedUrls(List<String> fileNames) {
        List<String> urls = new ArrayList<>();
        for (String fileName : fileNames) {
            String key = "temp/" + fileName;
            URL url = amazonS3.generatePresignedUrl(key,HttpMethod.PUT,1000*60*20L);
            urls.add(url.toString());
        }
        return urls;
    }

    @Transactional(readOnly = true)
    public String generateDownloadPreSignedUrl(String fileName) {
        try {
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            //첨부파일 다운로드
            URL url = amazonS3.generateDownloadPresignedUrl(bucketName,encodedFileName,1000 * 60 * 30L);
            return url.toString();
        } catch (Exception e) {
            throw new AttachCustomExceptionHandler(AttachErrorCode.S3_OPERATION_FAIL);
        }
    }

    //업로드 완료 후 Attach 등록 + 썸네일 생성
    @Timed(value = "s3_upload_presigned_complete", description = "presigned 업로드 완료 + 썸네일 생성", histogram = true)
    public List<AttachModel> createAttach(List<String> uploadedFileNames) {

        List<AttachModel> savedAttachModels = new ArrayList<>();
        List<CompletableFuture<Void>> thumbnailFutures = new ArrayList<>();

        for (String tempStoredFileName : uploadedFileNames) {
            try {
                String finalStoredFileName = tempStoredFileName.replaceFirst("^temp/", "final/");

                // 1. temp → final 복사
                amazonS3.copy(tempStoredFileName,finalStoredFileName);

                // 2. temp 삭제
                amazonS3.delete(tempStoredFileName);

                String fileUrl = amazonS3.getFileUrl(finalStoredFileName);

                long fileSize = amazonS3.fileSize(finalStoredFileName);

                AttachModel attachModel = AttachModel.builder()
                        .originFileName(finalStoredFileName)
                        .storedFileName(finalStoredFileName)
                        .filePath(fileUrl)
                        .fileSize(fileSize)
                        .build();

                AttachModel savedAttach = attachRepository.createAttach(attachModel);
                savedAttachModels.add(savedAttach);
                // 로깅
                MDC.put("flow", "presigned-complete");
                MDC.put("storedFile", finalStoredFileName);
                MDC.put("attachId", String.valueOf(savedAttach.getId()));

                // 비동기로 썸네일 생성
                try{
                    CompletableFuture<Void> future = thumbnailService.createAndUploadThumbnail(savedAttach);
                    thumbnailFutures.add(future);
                } finally {
                    MDC.remove("flow");
                    MDC.remove("storedFile");
                    MDC.remove("attachId");
                }
            } catch (Exception e) {
                log.error("[파일 업로드 실패] 파일: {}", tempStoredFileName, e);
                throw new AttachCustomExceptionHandler(AttachErrorCode.S3_OPERATION_FAIL);
            }
        }

        // 6. 모든 썸네일 생성 작업 병렬 완료까지 대기
        CompletableFuture.allOf(thumbnailFutures.toArray(new CompletableFuture[0])).join();
        log.info("[썸네일 생성 완료] 전체 {}건", thumbnailFutures.size());

        return savedAttachModels;
    }

    //  S3 파일 삭제 (원본 + 썸네일)
    @Transactional
    public void deleteFileFromS3(String storedFileName) {
        try {
            amazonS3.delete(storedFileName);
            log.info("[S3 파일 삭제 완료] 원본: {}", storedFileName);

            String thumbnailFileName = "thumb_" + storedFileName;
            amazonS3.delete(thumbnailFileName);
            log.info("[S3 썸네일 삭제 완료] 썸네일: {}", thumbnailFileName);

        } catch (Exception e) {
            log.error("[S3 파일 삭제 실패]", e);
            throw new AttachCustomExceptionHandler(AttachErrorCode.S3_DELETE_FAIL);
        }
    }

    // Attach + 파일 삭제
    @Transactional
    public void deleteAttachAndFile(Long attachId) {
        AttachModel attachModel = attachRepository.findById(attachId);

        // S3에서 파일 삭제
        deleteFileFromS3(attachModel.getStoredFileName());

        // DB에서 Attach 삭제
        attachRepository.deleteAttach(attachId);

        log.info("[Attach 삭제 완료] attachId = {}", attachId);
    }

    public void updateScheduleId(List<Long> fileIds, Long scheduleId) {
        attachRepository.updateScheduleId(fileIds, scheduleId);
    }

    //일반적인 S3 업로드 histogram = true를 설정하면 Grafana에서 평균/최댓값/퍼센타일까지 확인 가능
    @Timed(value = "s3_upload_direct", description = "직접 업로드 + 썸네일 생성 시간", histogram = true)
    public List<AttachModel> uploadDirectToS3WithThumbnail(List<MultipartFile> files) throws IOException {
        List<AttachModel> savedModels = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String originalFileName = file.getOriginalFilename();
                String storedFileName = System.currentTimeMillis() + "_" + originalFileName;
                MDC.put("uploadType", "direct");
                MDC.put("fileName", originalFileName);
                MDC.put("uploadSize", String.valueOf(file.getSize()));

                // 1. S3에 원본 업로드
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(file.getSize());
                metadata.setContentType(file.getContentType());

                amazonS3.upload(storedFileName,file.getInputStream(),metadata);
                String fileUrl = amazonS3.getFileUrl(storedFileName);

                // 2. 썸네일 동기 생성
                ByteArrayOutputStream thumbOut = new ByteArrayOutputStream();
                BufferedImage image = ImageIO.read(file.getInputStream());

                Thumbnails.of(image)
                        .size(thumbnailWidth, thumbnailHeight)
                        .outputFormat("jpg")
                        .toOutputStream(thumbOut);

                byte[] thumbBytes = thumbOut.toByteArray();
                ByteArrayInputStream thumbIn = new ByteArrayInputStream(thumbBytes);

                ObjectMetadata thumbMetadata = new ObjectMetadata();
                thumbMetadata.setContentLength(thumbBytes.length);
                thumbMetadata.setContentType("image/jpeg");

                String thumbFileName = "thumb_" + storedFileName;
                amazonS3.upload(thumbFileName,thumbIn,metadata);
                String thumbnailUrl = amazonS3.getFileUrl(thumbFileName);

                // 3. DB 저장
                AttachModel attachModel = AttachModel.builder()
                        .originFileName(originalFileName)
                        .storedFileName(storedFileName)
                        .filePath(fileUrl)
                        .fileSize(file.getSize())
                        .thumbnailFilePath(thumbnailUrl)
                        .build();

                AttachModel saved = attachRepository.createAttach(attachModel);
                savedModels.add(saved);
                log.info("[업로드 완료] {} ({} bytes)", originalFileName, file.getSize());
            } finally {
                MDC.clear();
            }
        }

        return savedModels;
    }
}

