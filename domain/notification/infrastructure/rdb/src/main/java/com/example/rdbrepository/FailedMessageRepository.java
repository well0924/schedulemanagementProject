package com.example.rdbrepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FailedMessageRepository extends JpaRepository<FailedMessage,Long> {

    List<FailedMessage> findByResolvedFalse();

    @Query(value = "select count(f) > 0 from FailedMessage f where f.payload = :payload")
    boolean existsByPayLoad(@Param("payload") String payload);

    @Modifying
    @Query("DELETE FROM FailedMessage e WHERE e.resolved = true AND e.resolvedAt < :threshold")
    int deleteByResolvedIsTrueAndResolvedAtBefore(@Param("threshold") LocalDateTime threshold);

}
