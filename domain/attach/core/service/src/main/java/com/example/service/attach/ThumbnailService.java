package com.example.service.attach;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.example.attach.dto.AttachErrorCode;
import com.example.attach.exception.AttachCustomExceptionHandler;
import com.example.model.attach.AttachModel;
import com.example.model.attach.FailedThumbnailModel;
import com.example.outbound.attach.AttachOutConnector;
import com.example.s3.utile.FileUtile;
import com.example.service.failthumbnail.FailedThumbnailService;
import io.micrometer.core.annotation.Timed;
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
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service("attachFailedThumbnailService")
@Transactional
@RequiredArgsConstructor
public class ThumbnailService {

    private final AmazonS3 amazonS3;

    private final AmazonS3Service amazonS3Service;

    private final AttachOutConnector attachOutConnector;

    private final FailedThumbnailService failedThumbnailService;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    @Value("${server.file.thumbnail-width:200}")
    private int thumbnailWidth;

    @Value("${server.file.thumbnail-height:200}")
    private int thumbnailHeight;


    @Timed(value = "s3_thumbnail_generation", description = "썸네일 생성 시간", histogram = true)
    @Async("threadPoolTaskExecutor")
    public CompletableFuture<Void> createAndUploadThumbnail(AttachModel attachModel) {
        String fileName = attachModel.getStoredFileName();
        try {
            String lower = fileName.toLowerCase();
            // 1. 이미지 파일 여부 체크
            if (!FileUtile.isSupportedImageExtension(lower)) {
                log.info("[섬네일 건너뜀] 이미지 파일 아님: {}", lower);
                return CompletableFuture.completedFuture(null);
            }

            log.info("[썸네일 생성 시작] {}", attachModel.getStoredFileName());

            S3Object s3Object = amazonS3.getObject(bucketName, attachModel.getStoredFileName());
            InputStream inputStream = s3Object.getObjectContent();

            if (!FileUtile.isSupportedImageExtension(lower)) {
                log.info("[섬네일 건너뜀] MIME 타입으로 확인한 결과 이미지 아님: {}", lower);
                return CompletableFuture.completedFuture(null);
            }

            BufferedImage originalImage = ImageIO.read(inputStream);

            if (originalImage == null) {
                throw new IllegalArgumentException("썸네일 생성 실패: 유효하지 않은 이미지: " + attachModel.getStoredFileName());
            }

            ByteArrayOutputStream thumbnailOutputStream = new ByteArrayOutputStream();

            // 섬네일 생성.
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

            String thumbnailUrl = amazonS3Service.getFileUrl(thumbnailFileName);

            attachModel.setThumbnailFilePath(thumbnailUrl);
            attachOutConnector.updateAttach(attachModel.getId(), attachModel);

            log.info("[썸네일 업로드 완료] {}", thumbnailUrl);

            CompletableFuture<Void> done = new CompletableFuture<>();
            done.complete(null);
            return done;
        } catch (Exception e) {
            log.error("[썸네일 생성 실패]",e);

            FailedThumbnailModel model = FailedThumbnailModel
                    .builder()
                    .storedFileName(attachModel.getStoredFileName())
                    .retryCount(0)
                    .reason(e.getMessage())
                    .resolved(false)
                    .build();
            failedThumbnailService.save(model);
            throw new AttachCustomExceptionHandler(AttachErrorCode.THUMBNAIL_CREATE_FAIL);
        }
    }

}
