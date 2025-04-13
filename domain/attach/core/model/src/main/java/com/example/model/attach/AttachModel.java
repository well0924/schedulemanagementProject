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

    //파일 업로드
    /*public static List<AttachModel> uploadMultipleFiles(List<MultipartFile> fileList, String uploadDir) throws IOException {
        List<AttachModel> uploadedFiles = new ArrayList<>();

        if (!CollectionUtils.isEmpty(fileList)) {
            String absolutePath = new File(uploadDir).getAbsolutePath() + File.separator;

            File uploadDirectory = new File(absolutePath);
            if (!uploadDirectory.exists()) {
                boolean wasSuccessful = uploadDirectory.mkdirs();
                if (!wasSuccessful) {
                    throw new IOException("Failed to create upload directory: " + absolutePath);
                }
            }

            // 다중 파일 처리
            for (MultipartFile multipartFile : fileList) {
                if (!multipartFile.isEmpty()) {
                    String originFileName = multipartFile.getOriginalFilename();
                    String contentType = multipartFile.getContentType();
                    String ext = Objects.requireNonNull(originFileName).substring(originFileName.lastIndexOf(".") + 1).toLowerCase();
                    String thumbnailPath = null;

                    // 확장자 체크 (지원하는 이미지 확장자 목록)
                    List<String> supportedImageExtensions = Arrays.asList("jpg", "jpeg", "png", "bmp", "gif", "webp");
                    boolean isImage = supportedImageExtensions.contains(ext);

                    // 저장할 파일명 (UUID 사용)
                    String storedFileName = UUID.randomUUID() + "." + ext;
                    File file = new File(absolutePath + storedFileName);

                    // 파일 저장
                    multipartFile.transferTo(file);

                    // 확장자를 기반으로 이미지인지 판단하여 섬네일 생성
                    if (isImage) {
                        thumbnailPath = createThumbnail(file.getAbsolutePath(), absolutePath, storedFileName);
                        if (thumbnailPath == null) {
                        }
                    }

                    // AttachModel 객체 생성 및 추가
                    AttachModel attachModel = AttachModel.builder()
                            .originFileName(originFileName)
                            .storedFileName(storedFileName)
                            .thumbnailFilePath(thumbnailPath)
                            .fileSize(multipartFile.getSize())
                            .filePath(absolutePath + storedFileName)
                            .build();

                    uploadedFiles.add(attachModel);

                    // 파일 권한 설정
                    file.setWritable(true);
                    file.setReadable(true);

                    if (thumbnailPath != null) {
                    }
                }
            }
        }
        return uploadedFiles;
    }


    //파일 업로드 수정
    public static List<AttachModel> updateMultipleFiles(List<AttachModel> existingFiles, List<MultipartFile> newFiles, String uploadDir) throws IOException {
        List<AttachModel> updatedFiles;

        // 기존 파일 삭제
        for (AttachModel existingFile : existingFiles) {
            File oldFile = new File(uploadDir, existingFile.getStoredFileName());
            if (oldFile.exists()) {
                oldFile.delete();
            }
        }
        // 새 파일 저장 (기존 업로드 메서드와 동일한 로직 사용)
        updatedFiles = uploadMultipleFiles(newFiles, uploadDir);

        return updatedFiles;
    }

    //파일 업로드 삭제
    public void deleteFile(String uploadDir) {
        File fileToDelete = new File(uploadDir);
        if (fileToDelete.exists()) {
            fileToDelete.delete();
        }

        if (this.thumbnailFilePath != null) {
            File thumbnailToDelete = new File(this.thumbnailFilePath);
            if (thumbnailToDelete.exists()) {
                thumbnailToDelete.delete();
            }
        }
    }*/
}
