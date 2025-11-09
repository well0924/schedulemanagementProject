package com.example.outbound.notification;

import com.example.notification.mapper.NotificationEntityMapper;
import com.example.notification.mapper.NotificationMapper;
import com.example.notification.model.FailMessageModel;
import com.example.rdbrepository.FailedMessage;
import com.example.rdbrepository.FailedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailMessageOutConnector {

    private final FailedMessageRepository failedMessageRepository;

    private final NotificationMapper notificationMapper;

    private final NotificationEntityMapper notificationEntityMapper;

    public List<FailMessageModel> findByResolvedFalse (){
        return failedMessageRepository.findByResolvedFalse()
                .stream()
                .map(notificationMapper::toModel)
                .collect(Collectors.toList());
    }

    public FailMessageModel createFailMessage(FailMessageModel failMessageModel) {
        FailedMessage failedMessage = notificationEntityMapper.toEntity(failMessageModel);
        return notificationMapper.toModel(failedMessageRepository.save(failedMessage));
    }

    public FailMessageModel updateFailMessage(FailMessageModel model) {
        FailedMessage existing = failedMessageRepository.findById(model.getId())
                .orElseThrow(() -> new IllegalArgumentException("FailMessage not found: id=" + model.getId()));
        return notificationMapper
                .toModel(failedMessageRepository
                        .save(notificationEntityMapper
                                .toEntity(model)));
    }

    public boolean findByPayload(String payload) {
        return failedMessageRepository.existsByPayLoad(payload);
    }

    public int deleteByResolvedIsTrueAndResolvedAtBefore(LocalDateTime threshold) {
        return failedMessageRepository.deleteByResolvedIsTrueAndResolvedAtBefore(threshold);
    }

}
