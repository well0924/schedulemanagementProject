package com.example.s3.utile;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

@Slf4j
@Component
public class FileUtile {

    public static boolean isSupportedImageExtension(String fileName) {
        if (fileName == null) return false;

        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".bmp") ||
                lower.endsWith(".gif") || lower.endsWith(".webp");
    }

    public static boolean isImageMimeType(InputStream inputStream) {
        try {
            inputStream.mark(100);
            String mimeType = URLConnection.guessContentTypeFromStream(inputStream);
            inputStream.reset();
            return mimeType != null && mimeType.startsWith("image/");
        } catch (IOException e) {
            log.warn("[MIME 타입 판별 실패]", e);
            return false;
        }
    }
}
