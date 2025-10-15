package com.example.outbound.attach;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.example.interfaces.attach.AmazonS3Port;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class AmazonS3OutConnector implements AmazonS3Port {

    private final AmazonS3 amazonS3;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    @Override
    public URL generatePresignedUrl(String key, HttpMethod method, long expiresMillis) {
        Date expiration = new Date(System.currentTimeMillis()+ expiresMillis);
        GeneratePresignedUrlRequest generatePresignedUrlRequest =
                new GeneratePresignedUrlRequest(bucketName, key)
                        .withMethod(method)
                        .withExpiration(expiration);
        return amazonS3.generatePresignedUrl(generatePresignedUrlRequest);
    }

    @Override
    public void copy(String tempStoredFileName, String finalStoredFileName) {
        amazonS3.copyObject(bucketName, tempStoredFileName, bucketName, finalStoredFileName);
    }

    @Override
    public void delete(String tempStoredFileName) {
        amazonS3.deleteObject(bucketName,tempStoredFileName);
    }

    @Override
    public String getFileUrl(String finalStoredFileName) {
        return amazonS3.getUrl(bucketName,finalStoredFileName).toString();
    }

    @Override
    public Long fileSize(String finalStoredFileName) {
        ObjectMetadata metadata = amazonS3.getObjectMetadata(bucketName, finalStoredFileName);
        return metadata.getContentLength();
    }

    @Override
    public InputStream getObjectInputStream(String key) {
        S3Object s3Object = amazonS3.getObject(bucketName, key);
        return s3Object.getObjectContent();
    }

    @Override
    public void upload(String key, InputStream inputStream, ObjectMetadata metadata) {
        amazonS3.putObject(bucketName, key, inputStream, metadata);
    }

    @Override
    public URL generateDownloadPresignedUrl(String key, String encodedFileName, long expiresMillis) {
        Date expiration = new Date(System.currentTimeMillis() + expiresMillis);
        String contentDisposition = "attachment; filename*=UTF-8''" + encodedFileName;

        GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(bucketName, key)
                        .withMethod(HttpMethod.GET)
                        .withExpiration(expiration);
        request.addRequestParameter("response-content-disposition", contentDisposition);

        return amazonS3.generatePresignedUrl(request);
    }
}
