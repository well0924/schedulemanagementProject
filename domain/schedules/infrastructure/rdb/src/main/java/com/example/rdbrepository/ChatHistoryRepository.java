package com.example.rdbrepository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory,Long> {
    List<ChatHistory> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}
