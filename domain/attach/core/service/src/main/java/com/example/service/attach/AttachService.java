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
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    // S3 presingedurl적용.
    @Transactional(readOnly = true)
    public List<String> generatePreSignedUrls(List<String> fileNames) {
        List<String> urls = new ArrayList<>();
        for (String fileName : fileNames) {
            Date expiration = new Date();
            expiration.setTime(expiration.getTime() + 1000 * 60 * 20); // 20분

            GeneratePresignedUrlRequest generatePresignedUrlRequest =
                    new GeneratePresignedUrlRequest(bucketName, fileName)
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
    @Timed(value = "s3.upload.presigned.complete", description = "presigned 업로드 완료 + 썸네일 생성", histogram = true)
    public List<AttachModel> createAttach(List<String> uploadedFileNames) {

        List<AttachModel> savedAttachModels = new ArrayList<>();

        for (String storedFileName : uploadedFileNames) {
            String fileUrl = amazonS3.getUrl(bucketName, storedFileName).toString();
            ObjectMetadata metadata = amazonS3.getObjectMetadata(bucketName, storedFileName);
            long fileSize = metadata.getContentLength();

            AttachModel attachModel = AttachModel.builder()
                    .originFileName(storedFileName)
                    .storedFileName(storedFileName)
                    .filePath(fileUrl)
                    .fileSize(fileSize)
                    .build();

            AttachModel savedAttach = attachOutConnector.createAttach(attachModel);

            // 비동기로 썸네일 생성
            createAndUploadThumbnail(savedAttach);

            savedAttachModels.add(savedAttach);
        }

        return savedAttachModels;
    }

    //비동기 섬네일 이미지 생성. 비동기 MDC 적용
    @Async("threadPoolTaskExecutor")
    public CompletableFuture<Void> createAndUploadThumbnail(AttachModel attachModel) {
        try {
            String fileName = attachModel.getStoredFileName().toLowerCase();
            // 1. 이미지 파일 여부 체크
            if (!fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg")
                    && !fileName.endsWith(".png") && !fileName.endsWith(".bmp")
                    && !fileName.endsWith(".gif") && !fileName.endsWith(".webp")) {
                log.info("[섬네일 건너뜀] 이미지 파일 아님: {}", fileName);
                return CompletableFuture.completedFuture(null);
            }

            log.info("[썸네일 생성 시작] {}", attachModel.getStoredFileName());


            S3Object s3Object = amazonS3.getObject(bucketName, attachModel.getStoredFileName());
            InputStream inputStream = s3Object.getObjectContent();

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

    //일반적인 S3 업로드 histogram = true를 설정하면 Grafana에서 평균/최댓값/퍼센타일까지 확인 가능
    @Timed(value = "s3.upload.direct", description = "직접 업로드 + 썸네일 생성 시간", histogram = true)
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

                amazonS3.putObject(bucketName, storedFileName, file.getInputStream(), metadata);
                String fileUrl = amazonS3.getUrl(bucketName, storedFileName).toString();

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
                amazonS3.putObject(bucketName, thumbFileName, thumbIn, thumbMetadata);
                String thumbnailUrl = amazonS3.getUrl(bucketName, thumbFileName).toString();

                // 3. DB 저장
                AttachModel attachModel = AttachModel.builder()
                        .originFileName(originalFileName)
                        .storedFileName(storedFileName)
                        .filePath(fileUrl)
                        .fileSize(file.getSize())
                        .thumbnailFilePath(thumbnailUrl)
                        .build();

                AttachModel saved = attachOutConnector.createAttach(attachModel);
                savedModels.add(saved);
                log.info("[업로드 완료] {} ({} bytes)", originalFileName, file.getSize());
            } finally {
                MDC.clear();
            }
        }

        return savedModels;
    }
}
