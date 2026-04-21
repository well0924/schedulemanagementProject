package com.example.inbound.schedules;

import com.example.model.schedules.ChatHistoryModel;

import java.util.List;

public interface ChatHistoryPort {

    List<ChatHistoryModel> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    void save(ChatHistoryModel historyModel);
}
