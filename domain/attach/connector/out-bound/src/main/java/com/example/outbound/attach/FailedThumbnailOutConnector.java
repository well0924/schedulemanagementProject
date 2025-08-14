package com.example.outbound.attach;

import com.example.model.attach.FailedThumbnailModel;
import com.example.rdb.FailedThumbnail;
import com.example.rdb.FailedThumbnailRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class FailedThumbnailOutConnector {

    private final FailedThumbnailRepository failedThumbnailRepository;

    public FailedThumbnailModel save(FailedThumbnailModel model) {
        FailedThumbnail failedThumbnail = FailedThumbnail
                .builder()
                .id(model.getId())
                .storedFileName(model.getStoredFileName())
                .reason(model.getReason())
                .retryCount(0)
                .resolved(false)
                .lastTriedAt(LocalDateTime.now())
                .build();
        return toModel(failedThumbnailRepository.save(failedThumbnail));
    };

    public List<FailedThumbnailModel> findRetryTargets(int maxRetry, int limit) {
        return failedThumbnailRepository.findRetryTargets(maxRetry, PageRequest.of(0, limit))
                .stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    public void markResolved(Long id) {
        FailedThumbnail failedThumbnail = failedThumbnailRepository
                .findById(id)
                .orElseThrow(()->new RuntimeException("FailedThumbnail not found: " + id));
        failedThumbnail.markResolved();
    }

    public void increaseRetryCount(Long id, String errorMessage) {
        FailedThumbnail failedThumbnail = failedThumbnailRepository
                .findById(id)
                .orElseThrow(()->new RuntimeException("FailedThumbnail not found: " + id));
        failedThumbnail.increaseRetry(errorMessage);
    }

    private FailedThumbnailModel toModel(FailedThumbnail failedThumbnail) {
        return FailedThumbnailModel
                .builder()
                .id(failedThumbnail.getId())
                .storedFileName(failedThumbnail.getStoredFileName())
                .lastTriedAt(failedThumbnail.getLastTriedAt())
                .reason(failedThumbnail.getReason())
                .retryCount(failedThumbnail.getRetryCount())
                .resolved(failedThumbnail.isResolved())
                .build();
    }
}
