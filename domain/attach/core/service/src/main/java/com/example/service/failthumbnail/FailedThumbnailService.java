package com.example.service.failthumbnail;

import com.example.model.attach.FailedThumbnailModel;
import com.example.service.port.FailedThumbnailPort;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service("failThumbnailService")
@Transactional
@AllArgsConstructor
public class FailedThumbnailService {

    private final FailedThumbnailPort failedThumbnailPort;

    public FailedThumbnailModel save(FailedThumbnailModel model) {
        return failedThumbnailPort.save(model);
    }

    @Transactional(readOnly = true)
    public List<FailedThumbnailModel> findRetryTargets(int maxRetry, int limit) {
        return failedThumbnailPort.findRetryTargets(maxRetry,limit);
    }

    public void markResolved(Long id) {
        failedThumbnailPort.markResolved(id);
    }

    public void increaseRetryCount(Long id, String errorMessage) {
        failedThumbnailPort.increaseRetryCount(id, errorMessage);
    }
}
