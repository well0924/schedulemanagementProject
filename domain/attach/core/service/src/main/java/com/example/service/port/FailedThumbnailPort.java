package com.example.service.port;

import com.example.model.attach.FailedThumbnailModel;

import java.util.List;

public interface FailedThumbnailPort {

    FailedThumbnailModel save(FailedThumbnailModel model);
    List<FailedThumbnailModel> findRetryTargets(int maxRetry, int limit);
    void markResolved(Long id);
    void increaseRetryCount(Long id, String errorMessage);
}
