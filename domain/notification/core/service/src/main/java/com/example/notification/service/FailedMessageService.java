package com.example.notification.service;

import com.example.notification.model.FailMessageModel;
import com.example.outbound.notification.FailMessageOutConnector;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@AllArgsConstructor
public class FailedMessageService {

    private final FailMessageOutConnector failMessageOutConnector;

    @Transactional(readOnly = true)
    public List<FailMessageModel>findByResolvedFalse(){
        return failMessageOutConnector.findByResolvedFalse();
    }

    public FailMessageModel createFailMessage(FailMessageModel failMessageModel){
        return failMessageOutConnector.createFailMessage(failMessageModel);
    }

    @Transactional(readOnly = true)
    public boolean findByPayload(String payload){
        return failMessageOutConnector.findByPayload(payload);
    }

    public FailMessageModel updateFailMessage(FailMessageModel model) {
        return failMessageOutConnector.updateFailMessage(model);
    }
}
