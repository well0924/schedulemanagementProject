package com.example.events.process;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity,Long> {
    // 중복 로직 확인
    boolean existsByEventId(String eventId);
}
