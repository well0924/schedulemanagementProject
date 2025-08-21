package com.example.service.failthumbnail;

import com.example.model.attach.FailedThumbnailModel;
import com.example.outbound.attach.FailedThumbnailOutConnector;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@AllArgsConstructor
public class FailedThumbnailService {

    private final FailedThumbnailOutConnector failedThumbnailOutConnector;

    public FailedThumbnailModel save(FailedThumbnailModel model) {
        return failedThumbnailOutConnector.save(model);
    }

    @Transactional(readOnly = true)
    public List<FailedThumbnailModel> findRetryTargets(int maxRetry, int limit) {
        return failedThumbnailOutConnector.findRetryTargets(maxRetry,limit);
    }

    public void markResolved(Long id) {
        failedThumbnailOutConnector.markResolved(id);
    }

    public void increaseRetryCount(Long id, String errorMessage) {
        failedThumbnailOutConnector.increaseRetryCount(id, errorMessage);
    }
}
