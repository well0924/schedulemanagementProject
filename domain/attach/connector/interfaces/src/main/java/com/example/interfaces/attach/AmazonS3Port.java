package com.example.interfaces.attach;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.ObjectMetadata;
import java.io.InputStream;
import java.net.URL;

public interface AmazonS3Port {
    URL generatePresignedUrl(String key, HttpMethod method, long expiresMillis);
    void copy(String tempStoredFileName, String finalStoredFileName);
    void delete(String tempStoredFileName);
    String getFileUrl(String finalStoredFileName);
    Long fileSize(String finalStoredFileName);
    InputStream getObjectInputStream(String key);
    void upload(String key, InputStream inputStream, ObjectMetadata metadata);
    URL generateDownloadPresignedUrl(String key, String encodedFileName, long expiresMillis);
}
