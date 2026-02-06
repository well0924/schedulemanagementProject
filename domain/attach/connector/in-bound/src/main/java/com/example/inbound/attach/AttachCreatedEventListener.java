package com.example.inbound.attach;

import com.example.events.spring.AttachCreatedEvent;
import com.example.interfaces.attach.AttachRepositoryPort;
import com.example.model.attach.AttachModel;
import com.example.service.attach.ThumbnailService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AttachCreatedEventListener {

    private final ThumbnailService thumbnailService;
    private final AttachRepositoryPort attachRepository;

    @Async("threadPoolTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AttachCreatedEvent event) {

        AttachModel attach = attachRepository.findById(event.getAttachId());
        thumbnailService.createAndUploadThumbnail(attach);
    }
}
