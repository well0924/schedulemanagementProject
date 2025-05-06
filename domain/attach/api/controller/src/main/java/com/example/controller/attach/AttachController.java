package com.example.controller.attach;

import com.example.inbound.attach.AttachInConnector;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

import static com.example.apimodel.attach.AttachApiModel.AttachResponse;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/attach")
public class AttachController {

    private final AttachInConnector attachInConnector;

    @GetMapping("/")
    public ResponseEntity<List<AttachResponse>> findAll() {
        List<AttachResponse> attachResponseList = attachInConnector.findAll();
        return ResponseEntity.status(HttpStatus.OK).body(attachResponseList);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AttachResponse> findById(@PathVariable("id") Long attachId) {
        return ResponseEntity.status(HttpStatus.OK).body(attachInConnector.findById(attachId));
    }

    @PostMapping("/presigned-urls")
    public ResponseEntity<List<String>> generatePreSignedUrls(@RequestBody List<String> fileNames) {
        List<String> urls = attachInConnector.generatePreSignedUrls(fileNames);
        return ResponseEntity.ok(urls);
    }

    @GetMapping("/{id}/presigned-download-url")
    public ResponseEntity<String> getPreSignedDownloadUrl(@PathVariable("id") Long attachId) {
        try {
            AttachResponse attach = attachInConnector.findById(attachId);
            String preSignedUrl = attachInConnector.generateDownloadPreSignedUrl(attach.originFileName());
            return ResponseEntity.ok(preSignedUrl);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Presigned Download URL 생성 실패");
        }
    }

    // 업로드 완료 후 Attach 등록
    @PostMapping("/complete-upload")
    public ResponseEntity<List<AttachResponse>> completeUpload(@RequestBody List<String> uploadedFileNames) throws IOException {
        List<AttachResponse> files = attachInConnector.createdAttach(uploadedFileNames);
        return ResponseEntity.ok(files);
    }

    // 파일 + Attach 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttach(@PathVariable("id") Long attachId) {
        attachInConnector.deleteAttach(attachId);
        return ResponseEntity.noContent().build();
    }
}
