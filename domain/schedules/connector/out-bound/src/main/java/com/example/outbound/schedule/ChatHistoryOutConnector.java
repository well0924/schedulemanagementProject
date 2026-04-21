package com.example.outbound.schedule;

import com.example.inbound.schedules.ChatHistoryPort;
import com.example.model.schedules.ChatHistoryModel;
import com.example.rdbrepository.ChatHistory;
import com.example.rdbrepository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ChatHistoryOutConnector implements ChatHistoryPort {

    private final ChatHistoryRepository chatHistoryRepository;

    @Override
    public List<ChatHistoryModel> findByMemberIdOrderByCreatedAtDesc(Long memberId) {
        return chatHistoryRepository
                .findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(chatHistory -> ChatHistoryModel
                        .builder()
                        .id(chatHistory.getId())
                        .memberId(chatHistory.getMemberId())
                        .userMessage(chatHistory.getUserMessage())
                        .assistantResponse(chatHistory.getAssistantResponse())
                        .createdAt(chatHistory.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void save(ChatHistoryModel historyModel) {
        ChatHistory chatHistory = ChatHistory
                .builder()
                .id(historyModel.getId())
                .memberId(historyModel.getMemberId())
                .assistantResponse(historyModel.getAssistantResponse())
                .userMessage(historyModel.getUserMessage())
                .createdAt(historyModel.getCreatedAt())
                .build();

        chatHistoryRepository.save(chatHistory);
    }
}
