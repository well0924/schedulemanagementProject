package com.example.service.attach;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.example.attach.dto.AttachErrorCode;
import com.example.attach.exception.AttachCustomExceptionHandler;
import com.example.model.attach.AttachModel;
import com.example.outbound.attach.AttachOutConnector;
import com.example.s3.utile.FileUtile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AttachService {

    private final AttachOutConnector attachOutConnector;

    private final AmazonS3 amazonS3;

    private final FileUtile fileUtile;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    @Value("${server.file.thumbnail-width:200}")
    private int thumbnailWidth;

    @Value("${server.file.thumbnail-height:200}")
    private int thumbnailHeight;

    @Transactional(readOnly = true)
    public List<AttachModel> findAll() {
        return attachOutConnector.findAll();
    }

    @Transactional(readOnly = true)
    public AttachModel findById(Long attachId) {
        return attachOutConnector.findById(attachId);
    }

    @Transactional(readOnly = true)
    public List<AttachModel> findByIds(List<Long>attachIds) {
        return attachOutConnector.findByIdIn(attachIds);
    }

    @Transactional(readOnly = true)
    public AttachModel findByOriginFileName(String originFileName) {
        return attachOutConnector.findByOriginFileName(originFileName);
    }

    @Transactional(readOnly = true)
    public AttachModel findByStoredFileName(String storedFileName) {
        return attachOutConnector.findByStoredFileName(storedFileName);
    }

    // S3 presingedurl적용.
    @Transactional(readOnly = true)
    public List<String> generatePreSignedUrls(List<String> fileNames) {
        List<String> urls = new ArrayList<>();
        for (String fileName : fileNames) {
            String key = "temp/" + fileName;
            Date expiration = new Date();
            expiration.setTime(expiration.getTime() + 1000 * 60 * 20); // 20분

            GeneratePresignedUrlRequest generatePresignedUrlRequest =
                    new GeneratePresignedUrlRequest(bucketName, key)
                            .withMethod(HttpMethod.PUT)
                            .withExpiration(expiration);

            URL url = amazonS3.generatePresignedUrl(generatePresignedUrlRequest);
            urls.add(url.toString());
        }
        return urls;
    }

    @Transactional(readOnly = true)
    public String generateDownloadPreSignedUrl(String fileName) {
        Date expiration = new Date();
        expiration.setTime(expiration.getTime() + 1000 * 60 * 30); // 30분 유효

        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        String contentDisposition = "attachment; filename*=UTF-8''" + encodedFileName;

        GeneratePresignedUrlRequest generatePresignedUrlRequest =
                new GeneratePresignedUrlRequest(bucketName, fileName)
                        .withMethod(HttpMethod.GET)  // 다운로드용은 GET
                        .withExpiration(expiration);
        //첨부파일 다운로드
        generatePresignedUrlRequest.addRequestParameter("response-content-disposition", contentDisposition);
        URL url = amazonS3.generatePresignedUrl(generatePresignedUrlRequest);
        return url.toString();
    }

    //업로드 완료 후 Attach 등록 + 썸네일 생성
    public List<AttachModel> createAttach(List<String> uploadedFileNames) {

        List<AttachModel> savedAttachModels = new ArrayList<>();
        List<CompletableFuture<Void>> thumbnailFutures = new ArrayList<>();

        for (String tempStoredFileName : uploadedFileNames) {
            try {
                String finalStoredFileName = tempStoredFileName.replaceFirst("^temp/", "final/");

                // 1. temp → final 복사
                amazonS3.copyObject(bucketName, tempStoredFileName, bucketName, finalStoredFileName);

                // 2. temp 삭제
                amazonS3.deleteObject(bucketName, tempStoredFileName);

                String fileUrl = amazonS3.getUrl(bucketName, finalStoredFileName).toString();
                ObjectMetadata metadata = amazonS3.getObjectMetadata(bucketName, finalStoredFileName);
                long fileSize = metadata.getContentLength();

                AttachModel attachModel = AttachModel.builder()
                        .originFileName(finalStoredFileName)
                        .storedFileName(finalStoredFileName)
                        .filePath(fileUrl)
                        .fileSize(fileSize)
                        .build();

                AttachModel savedAttach = attachOutConnector.createAttach(attachModel);
                savedAttachModels.add(savedAttach);

                // 비동기로 썸네일 생성
                CompletableFuture<Void> future = createAndUploadThumbnail(savedAttach);
                thumbnailFutures.add(future);

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

    //비동기 섬네일 이미지 생성.
    @Async
    public CompletableFuture<Void> createAndUploadThumbnail(AttachModel attachModel) {
        try {
            String fileName = attachModel.getStoredFileName().toLowerCase();
            // 1. 이미지 파일 여부 체크
            if (!fileUtile.isSupportedImageExtension(fileName)) {
                log.info("[섬네일 건너뜀] 이미지 파일 아님: {}", fileName);
                return CompletableFuture.completedFuture(null);
            }

            log.info("[썸네일 생성 시작] {}", attachModel.getStoredFileName());

            S3Object s3Object = amazonS3.getObject(bucketName, attachModel.getStoredFileName());
            InputStream inputStream = s3Object.getObjectContent();

            if (!fileUtile.isSupportedImageExtension(fileName)) {
                log.info("[섬네일 건너뜀] MIME 타입으로 확인한 결과 이미지 아님: {}", attachModel.getStoredFileName());
                return CompletableFuture.completedFuture(null);
            }
            
            BufferedImage originalImage = ImageIO.read(inputStream);

            if (originalImage == null) {
                throw new IllegalArgumentException("썸네일 생성 실패: 유효하지 않은 이미지: " + attachModel.getStoredFileName());
            }

            ByteArrayOutputStream thumbnailOutputStream = new ByteArrayOutputStream();

            Thumbnails.of(originalImage)
                    .size(thumbnailWidth, thumbnailHeight)
                    .outputFormat("jpg")
                    .toOutputStream(thumbnailOutputStream);

            byte[] thumbnailBytes = thumbnailOutputStream.toByteArray();
            ByteArrayInputStream thumbnailInputStream = new ByteArrayInputStream(thumbnailBytes);

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(thumbnailBytes.length);
            metadata.setContentType("image/jpeg");

            String thumbnailFileName = "thumb_" + attachModel.getStoredFileName();
            amazonS3.putObject(bucketName, thumbnailFileName, thumbnailInputStream, metadata);

            String thumbnailUrl = amazonS3.getUrl(bucketName, thumbnailFileName).toString();

            attachModel.setThumbnailFilePath(thumbnailUrl);
            attachOutConnector.updateAttach(attachModel.getId(), attachModel);

            log.info("[썸네일 업로드 완료] {}", thumbnailUrl);

            CompletableFuture<Void> done = new CompletableFuture<>();
            done.complete(null);
            return done;
        } catch (Exception e) {
            log.error("[썸네일 생성 실패]", e);
            throw new AttachCustomExceptionHandler(AttachErrorCode.THUMBNAIL_CREATE_FAIL);
        }
    }

    //  S3 파일 삭제 (원본 + 썸네일)
    @Transactional
    public void deleteFileFromS3(String storedFileName) {
        try {
            amazonS3.deleteObject(bucketName, storedFileName);
            log.info("[S3 파일 삭제 완료] 원본: {}", storedFileName);

            String thumbnailFileName = "thumb_" + storedFileName;
            amazonS3.deleteObject(bucketName, thumbnailFileName);
            log.info("[S3 썸네일 삭제 완료] 썸네일: {}", thumbnailFileName);

        } catch (Exception e) {
            log.error("[S3 파일 삭제 실패]", e);
            throw new RuntimeException("S3 파일 삭제 실패", e);
        }
    }

    // Attach + 파일 삭제
    @Transactional
    public void deleteAttachAndFile(Long attachId) {
        AttachModel attachModel = attachOutConnector.findById(attachId);

        // S3에서 파일 삭제
        deleteFileFromS3(attachModel.getStoredFileName());

        // DB에서 Attach 삭제
        attachOutConnector.deleteAttach(attachId);

        log.info("[Attach 삭제 완료] attachId = {}", attachId);
    }

    public void updateScheduleId(List<Long> fileIds, Long scheduleId) {
        attachOutConnector.updateScheduleId(fileIds, scheduleId);
    }
}

