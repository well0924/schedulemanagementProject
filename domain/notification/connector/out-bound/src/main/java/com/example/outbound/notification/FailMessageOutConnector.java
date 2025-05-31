package com.example.outbound.notification;

import com.example.notification.model.FailMessageModel;
import com.example.rdbrepository.FailedMessage;
import com.example.rdbrepository.FailedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailMessageOutConnector {

    private final FailedMessageRepository failedMessageRepository;

    public List<FailMessageModel> findByResolvedFalse (){
        return failedMessageRepository.findByResolvedFalse()
                .stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    public FailMessageModel createFailMessage(FailMessageModel failMessageModel) {
        FailedMessage failedMessage = FailedMessage
                .builder()
                .id(failMessageModel.getId())
                .messageType(failMessageModel.getMessageType())
                .exceptionMessage(failMessageModel.getExceptionMessage())
                .topic(failMessageModel.getTopic())
                .payload(failMessageModel.getPayload())
                .retryCount(failMessageModel.getRetryCount())
                .resolved(failMessageModel.isResolved())
                .createdAt(failMessageModel.getCreatedAt())
                .lastTriedAt(failMessageModel.getLastTriedAt())
                .build();

        return toModel(failedMessageRepository.save(failedMessage));
    }

    public FailMessageModel updateFailMessage(FailMessageModel model) {
        FailedMessage existing = failedMessageRepository.findById(model.getId())
                .orElseThrow(() -> new IllegalArgumentException("FailMessage not found: id=" + model.getId()));

        FailedMessage updated = FailedMessage.builder()
                .id(existing.getId())
                .topic(existing.getTopic())
                .messageType(existing.getMessageType())
                .payload(existing.getPayload())
                .createdAt(existing.getCreatedAt())
                .resolved(model.isResolved())
                .retryCount(model.getRetryCount())
                .lastTriedAt(model.getLastTriedAt())
                .exceptionMessage(model.getExceptionMessage())
                .build();

        return toModel(failedMessageRepository.save(updated));
    }

    public boolean findByPayload(String payload) {
        return failedMessageRepository.existsByPayLoad(payload);
    }


    private FailMessageModel toModel(FailedMessage failedMessage){
        return FailMessageModel
                .builder()
                .id(failedMessage.getId())
                .exceptionMessage(failedMessage.getExceptionMessage())
                .topic(failedMessage.getTopic())
                .payload(failedMessage.getPayload())
                .resolved(failedMessage.isResolved())
                .retryCount(failedMessage.getRetryCount())
                .createdAt(failedMessage.getCreatedAt())
                .lastTriedAt(failedMessage.getLastTriedAt())
                .build();
    }
}
