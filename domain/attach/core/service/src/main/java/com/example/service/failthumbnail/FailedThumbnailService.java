package com.example.service.failthumbnail;

import com.example.model.attach.FailedThumbnailModel;

import com.example.outbound.attach.FailedThumbnailOutConnector;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service("failThumbnailService")
@Transactional
@AllArgsConstructor
public class FailedThumbnailService {

    private final FailedThumbnailOutConnector failedThumbnail;

    public FailedThumbnailModel save(FailedThumbnailModel model) {
        return failedThumbnail.save(model);
    }

    @Transactional(readOnly = true)
    public List<FailedThumbnailModel> findRetryTargets(int maxRetry, int limit) {
        return failedThumbnail.findRetryTargets(maxRetry,limit);
    }

    public void markResolved(Long id) {
        failedThumbnail.markResolved(id);
    }

    public void increaseRetryCount(Long id, String errorMessage) {
        failedThumbnail.increaseRetryCount(id, errorMessage);
    }
}
