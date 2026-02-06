package com.example.service.attach;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.example.attach.dto.AttachErrorCode;
import com.example.attach.exception.AttachCustomExceptionHandler;
import com.example.model.attach.AttachModel;
import com.example.model.attach.FailedThumbnailModel;
import com.example.outbound.attach.AmazonS3OutConnector;
import com.example.outbound.attach.AttachOutConnector;
import com.example.outbound.attach.FailedThumbnailOutConnector;
import com.example.s3.utile.FileUtile;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Slf4j
@Service("attachFailedThumbnailService")
@Transactional
@RequiredArgsConstructor
public class ThumbnailService {

    private final AmazonS3OutConnector amazonS3;

    private final AttachOutConnector attachRepository;

    private final FailedThumbnailOutConnector failedThumbnail;

    @Value("${server.file.thumbnail-width:200}")
    private int thumbnailWidth;

    @Value("${server.file.thumbnail-height:200}")
    private int thumbnailHeight;


    @Timed(value = "s3_thumbnail_generation", description = "썸네일 생성 시간", histogram = true)
    public void createAndUploadThumbnail(AttachModel attachModel) {
        String fileName = attachModel.getStoredFileName();
        log.info(fileName);
        try {
            String lower = fileName.toLowerCase();
            log.info(lower);
            // 1. 이미지 파일 여부 체크
            if (!FileUtile.isSupportedImageExtension(lower)) {
                log.info("[섬네일 건너뜀] 이미지 파일 아님: {}", lower);
                return;
            }

            log.info("[썸네일 생성 시작] {}", attachModel.getStoredFileName());

            InputStream inputStream = amazonS3.getObjectInputStream(fileName);

            BufferedImage originalImage = ImageIO.read(inputStream);
            log.info(originalImage.toString());
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
            // thumbNail 파일명
            String thumbnailFileName = "thumb_" + attachModel.getStoredFileName();
            log.info(thumbnailFileName);
            // S3에 업로드
            amazonS3.upload(thumbnailFileName, thumbnailInputStream, metadata);
            // thumbNail 경로
            String thumbnailUrl = amazonS3.getFileUrl(thumbnailFileName);
            // 섬네일 업데이트
            attachModel.setThumbnailFilePath(thumbnailUrl);
            attachRepository.updateAttach(attachModel.getId(), attachModel);

            log.info("[썸네일 업로드 완료] {}", thumbnailUrl);

        } catch (Exception e) {
            log.error("[썸네일 생성 실패]",e);

            FailedThumbnailModel model = FailedThumbnailModel
                    .builder()
                    .storedFileName(attachModel.getStoredFileName())
                    .retryCount(0)
                    .reason(e.getMessage())
                    .resolved(false)
                    .build();
            failedThumbnail.save(model);
            throw new AttachCustomExceptionHandler(AttachErrorCode.THUMBNAIL_CREATE_FAIL);
        }
    }

}
