package com.example.model.attach;

import lombok.*;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachModel {

    private Long id;
    private String thumbnailFilePath;
    private String originFileName;
    private String storedFileName;
    private String filePath;
    private Long fileSize;
    private Long scheduledId;
    private boolean isDeletedAttached;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    //presigned url 생성
    /*public String generatePreSignedUrl(String secretKey, long expirationTime) throws Exception {
        long expirationTimestamp = System.currentTimeMillis() + expirationTime;
        String dataToSign = originFileName + ":" + expirationTimestamp;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8)));

        return "/files/download?fileName=" + originFileName
                + "&expiration=" + expirationTimestamp
                + "&signature=" + signature;
    }*/
    
    // presigned url 유효성 검사
    /*public boolean validatePreSignedUrl(String secretKey, long expiration, String signature) throws Exception {
        if (System.currentTimeMillis() > expiration) {
            return false;
        }

        String dataToSign = originFileName + ":" + expiration;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

        String expectedSignature = Base64.getEncoder().encodeToString(mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8)));
        return expectedSignature.equals(signature);
    }*/

    public static String createThumbnail(String originalFilePath, String uploadDir, String thumbnailName) {
        try {
            // 원본 이미지가 존재하는지 확인
            File originalFile = new File(originalFilePath);
            if (!originalFile.exists()) {
                return null;
            }

            // 원본 이미지가 정상적으로 읽히는지 확인
            BufferedImage originalImage = ImageIO.read(originalFile);
            if (originalImage == null) {
                return null;
            }

            // 썸네일 저장 경로 설정
            String thumbnailPath = uploadDir + File.separator + "thumb_" + thumbnailName;
            File thumbnailFile = new File(thumbnailPath);

            // 썸네일 생성 (JPG로 변환 강제 적용)
            Thumbnails.of(originalImage)
                    .size(200, 200)
                    .outputFormat("jpg")  // 확실하게 JPG로 변환
                    .toFile(thumbnailFile);

            // 생성된 파일이 정상적으로 존재하는지 확인
            if (!thumbnailFile.exists()) {
                return null;
            }

            return thumbnailPath;
        } catch (IOException e) {
            return null;
        }
    }

}
