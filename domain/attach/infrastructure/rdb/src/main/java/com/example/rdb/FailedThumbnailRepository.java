package com.example.rdb;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FailedThumbnailRepository extends JpaRepository<FailedThumbnail,Long> {

    @Query("SELECT f FROM FailedThumbnail f WHERE f.resolved = false AND f.retryCount < :maxRetry")
    List<FailedThumbnail> findRetryTargets(int maxRetry, Pageable pageable);
}
