package com.example.events.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity,String> {

    List<OutboxEventEntity> findTop100BySentFalseOrderByCreatedAtAsc();
}
