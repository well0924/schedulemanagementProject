package com.example.service.attach;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.example.model.attach.AttachModel;
import com.example.outconnector.attach.AttachOutConnector;
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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
            expiration.setTime(expiration.getTime() + 1000 * 60 * 15); // 15분

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
        expiration.setTime(expiration.getTime() + 1000 * 60 * 10); // 10분 유효

        GeneratePresignedUrlRequest generatePresignedUrlRequest =
                new GeneratePresignedUrlRequest(bucketName, fileName)
                        .withMethod(HttpMethod.GET)  // 다운로드용은 GET
                        .withExpiration(expiration);

        URL url = amazonS3.generatePresignedUrl(generatePresignedUrlRequest);
        return url.toString();
    }

    //업로드 완료 후 Attach 등록 + 썸네일 생성
    public List<AttachModel> createAttach(List<String> uploadedFileNames) throws IOException {

        List<AttachModel> savedAttachModels = new ArrayList<>();

        for (String storedFileName : uploadedFileNames) {
            String fileUrl = amazonS3.getUrl(bucketName, storedFileName).toString();

            AttachModel attachModel = AttachModel.builder()
                    .originFileName(storedFileName)
                    .storedFileName(storedFileName)
                    .filePath(fileUrl)
                    .build();

            AttachModel savedAttach = attachOutConnector.createAttach(attachModel);

            // 비동기로 썸네일 생성
            createAndUploadThumbnail(savedAttach);

            savedAttachModels.add(savedAttach);
        }

        return savedAttachModels;
    }

    //비동기 섬네일 이미지 생성.
    @Async
    public CompletableFuture<Void> createAndUploadThumbnail(AttachModel attachModel) {
        try {
            log.info("[썸네일 생성 시작] {}", attachModel.getStoredFileName());

            S3Object s3Object = amazonS3.getObject(bucketName, attachModel.getStoredFileName());
            InputStream inputStream = s3Object.getObjectContent();

            BufferedImage originalImage = ImageIO.read(inputStream);
            if (originalImage == null) {
                throw new IllegalArgumentException("Invalid image file: " + attachModel.getStoredFileName());
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

            log.info("[썸네일 업로드 완료] {}", thumbnailUrl);

            CompletableFuture<Void> done = new CompletableFuture<>();
            done.complete(null);
            return done;

        } catch (Exception e) {
            log.error("[썸네일 생성 실패]", e);
            throw new RuntimeException("썸네일 생성 실패", e);
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


   /* public List<AttachModel> updateAttach(Long scheduleId, List<MultipartFile> newFiles) throws IOException {

        List<AttachModel> existFile = attachOutConnector.findAllByScheduleId(scheduleId);
        log.info("attachment:"+existFile);
        List<AttachModel> updatedFiles = AttachModel.updateMultipleFiles(existFile, newFiles, fileUploadPath);

        List<AttachModel> savedAttachModels = new ArrayList<>();

        for (AttachModel updatedFile : updatedFiles) {
            savedAttachModels.add(attachOutConnector.updateAttach(updatedFile.getId(), updatedFile));
        }
        log.info("updatedInfo::"+savedAttachModels);
        return savedAttachModels;
    }

    public void deleteAttach(Long attachId) {
        AttachModel attachModel = attachOutConnector.findById(attachId);
        attachModel.deleteFile(attachModel.getFilePath());
        attachOutConnector.deleteAttach(attachId);
    }*/

    public void updateScheduleId(List<Long> fileIds, Long scheduleId) {
        attachOutConnector.updateScheduleId(fileIds, scheduleId);
    }

}
