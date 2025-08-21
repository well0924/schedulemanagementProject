package com.example.service.attach;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class AmazonS3Service {

    private final AmazonS3 amazonS3;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    // presignedurl 생성
    public URL generatePresignedUrl(String key, HttpMethod method, long expiresMillis) {
        Date expiration = new Date(System.currentTimeMillis()+ expiresMillis);
        GeneratePresignedUrlRequest generatePresignedUrlRequest =
                new GeneratePresignedUrlRequest(bucketName, key)
                        .withMethod(method)
                        .withExpiration(expiration);
        return amazonS3.generatePresignedUrl(generatePresignedUrlRequest);
    }

    // S3 파일 경로 수정
    public void copy(String tempStoredFileName, String finalStoredFileName) {
        amazonS3.copyObject(bucketName, tempStoredFileName, bucketName, finalStoredFileName);
    }

    // S3 파일 삭제
    public void delete(String tempStoredFileName) {
        amazonS3.deleteObject(bucketName,tempStoredFileName);
    }

    // S3 파일 경로
    public String getFileUrl(String finalStoredFileName) {
        return amazonS3.getUrl(bucketName,finalStoredFileName).toString();
    }

    // S3 파일 사이즈
    public Long fileSize(String finalStoredFileName) {
        ObjectMetadata metadata = amazonS3.getObjectMetadata(bucketName, finalStoredFileName);
        return metadata.getContentLength();
    }

}
