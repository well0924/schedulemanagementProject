package com.example.attach;


import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.example.attach.exception.AttachCustomExceptionHandler;
import com.example.model.attach.AttachModel;
import com.example.outbound.attach.AttachOutConnector;
import com.example.s3.utile.FileUtile;
import com.example.service.attach.AttachService;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AttachUnitTest {

    @InjectMocks
    private AttachService attachService;

    @Mock
    private AttachOutConnector attachOutConnector;

    @Mock
    private AmazonS3 amazonS3;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName = "test-bucket";

    @BeforeEach
    void setUp() throws Exception {
        // bucketName 필드 리플렉션 주입
        Field bucketField = AttachService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(attachService, "test-bucket");

        // 썸네일 크기 값도 설정해주자 (디폴트 없으면 NPE 날 수 있음)
        Field widthField = AttachService.class.getDeclaredField("thumbnailWidth");
        widthField.setAccessible(true);
        widthField.set(attachService, 200);

        Field heightField = AttachService.class.getDeclaredField("thumbnailHeight");
        heightField.setAccessible(true);
        heightField.set(attachService, 200);
    }

    @Test
    @DisplayName("이미지 확장자 테스트")
    void ImageTypeTest() {
        assertTrue(FileUtile.isSupportedImageExtension("test.jpg"));
        assertTrue(FileUtile.isSupportedImageExtension("test.PNG"));
        assertFalse(FileUtile.isSupportedImageExtension("test.txt"));
    }

    @Test
    @DisplayName("이미지 MINE 타입 검사")
    void ImageMineTypeTest() throws IOException {
        BufferedImage dummy = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(dummy, "png", baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

        assertTrue(FileUtile.isImageMimeType(bais));
    }

    @Test
    @DisplayName("섬네일 생성 테스트")
    void ThumbNailCreateTest() throws IOException {
        // given
        String storedFileName = "final/test-image.jpg";
        AttachModel attachModel = AttachModel.builder()
                .id(1L)
                .storedFileName(storedFileName)
                .build();

        // S3에서 객체 가져오기 mocking
        BufferedImage dummyImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(dummyImage, "jpg", baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

        S3Object s3Object = new S3Object();
        s3Object.setObjectContent(bais);

        when(amazonS3.getObject(bucketName, storedFileName)).thenReturn(s3Object);
        when(amazonS3.getUrl(eq(bucketName), ArgumentMatchers.anyString()))
                .thenReturn(new URL("https://dummy-url.com/thumb_test-image.jpg"));

        // when
        CompletableFuture<Void> future = attachService.createAndUploadThumbnail(attachModel);
        future.join();

        // then
        verify(amazonS3, times(1)).putObject(eq(bucketName), startsWith("thumb_"), any(InputStream.class), any(ObjectMetadata.class));
        verify(attachOutConnector, times(1)).updateAttach(eq(1L), any(AttachModel.class));
    }


    @Test
    @DisplayName("비동기 병렬 썸네일 생성 테스트")
    void createAttachThumbNailCreateTest() throws Exception {
        // given
        List<String> uploadedFiles = List.of("temp/test1.jpg", "temp/test2.jpg");

        // S3 mocking
        for (String tempFile : uploadedFiles) {
            String finalFile = tempFile.replaceFirst("^temp/", "final/");
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(123L);

            when(amazonS3.getObjectMetadata("test-bucket", finalFile)).thenReturn(metadata);
            when(amazonS3.getUrl("test-bucket", finalFile)).thenReturn(new URL("https://dummy.com/" + finalFile));
        }

        // attach 저장 mocking
        when(attachOutConnector.createAttach(any())).thenAnswer(invocation -> {
            AttachModel model = invocation.getArgument(0);
            model.setId(new Random().nextLong());
            return model;
        });

        // 1번 이미지용 S3Object
        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB), "jpg", baos1);
        S3Object s3Object1 = new S3Object();
        s3Object1.setObjectContent(new ByteArrayInputStream(baos1.toByteArray()));

        // 2번 이미지용 S3Object
        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB), "jpg", baos2);
        S3Object s3Object2 = new S3Object();
        s3Object2.setObjectContent(new ByteArrayInputStream(baos2.toByteArray()));

        // getObject mocking: 각 파일명별로 다르게 리턴
        when(amazonS3.getObject("test-bucket", "final/test1.jpg")).thenReturn(s3Object1);
        when(amazonS3.getObject("test-bucket", "final/test2.jpg")).thenReturn(s3Object2);
        when(amazonS3.getUrl(eq("test-bucket"), startsWith("thumb_")))
                .thenReturn(new URL("https://dummy.com/thumb"));

        // when
        List<AttachModel> result = attachService.createAttach(uploadedFiles);

        // then
        Assert.assertEquals(2, result.size());

        // S3 동작 검증
        for (String tempFile : uploadedFiles) {
            String finalFile = tempFile.replaceFirst("^temp/", "final/");
            verify(amazonS3).copyObject("test-bucket", tempFile, "test-bucket", finalFile);
            verify(amazonS3).deleteObject("test-bucket", tempFile);
            verify(amazonS3).getObjectMetadata("test-bucket", finalFile);
        }

        // attach 저장 호출 확인
        verify(attachOutConnector, times(2)).createAttach(any());
        verify(attachOutConnector, times(2)).updateAttach(anyLong(), any());

        // 썸네일 S3 putObject 호출도 2회
        verify(amazonS3, times(2))
                .putObject(eq("test-bucket"), startsWith("thumb_"), any(), any());
    }

    @Test
    @DisplayName("PreSignedUrl 발급 테스트")
    void generatePreSignedUrlTest() {
        // given
        List<String> fileNames = List.of("test1.jpg", "test2.jpg");

        when(amazonS3.generatePresignedUrl(any())).thenAnswer(invocation -> {
            GeneratePresignedUrlRequest req = invocation.getArgument(0);
            return new URL("https://dummy.com/" + req.getKey());
        });

        // when
        List<String> result = attachService.generatePreSignedUrls(fileNames);

        // then
        assertEquals(2, result.size());
        assertTrue(result.get(0).contains("temp/test1.jpg"));
        assertTrue(result.get(1).contains("temp/test2.jpg"));
    }

    @Test
    @DisplayName("파일 + Attach 삭제 테스트")
    void deleteAttachAndFile_정상_삭제_테스트() {
        // given
        AttachModel attachModel = AttachModel.builder()
                .id(1L)
                .storedFileName("final/test-image.jpg")
                .build();

        when(attachOutConnector.findById(1L)).thenReturn(attachModel);

        // when
        attachService.deleteAttachAndFile(1L);

        // then
        verify(amazonS3).deleteObject("test-bucket", "final/test-image.jpg");
        verify(amazonS3).deleteObject("test-bucket", "thumb_final/test-image.jpg");
        verify(attachOutConnector).deleteAttach(1L);
    }

    @Test
    @DisplayName("createAttach 실패 시 AttachCustomExceptionHandler 발생")
    void createAttach_S3_예외_발생_테스트() {
        // given
        List<String> files = List.of("temp/test1.jpg");
        // S3 복사 단계에서 예외 발생 유도
        when(amazonS3.copyObject(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("S3 복사 실패"));

        // when & then
        assertThrows(AttachCustomExceptionHandler.class, () -> {
            attachService.createAttach(files);
        });
    }

    @Test
    @DisplayName("deleteFileFromS3 예외 발생 시 RuntimeException 처리")
    void deleteFileFromS3_예외_처리_테스트() {
        // given
        String fileName = "final/test.jpg";
        doThrow(new RuntimeException("S3 삭제 실패"))
                .when(amazonS3).deleteObject("test-bucket", fileName);

        // when & then
        assertThrows(RuntimeException.class, () -> {
            attachService.deleteFileFromS3(fileName);
        });
    }
}
