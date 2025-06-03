package com.example.outbound.email;

import com.example.notification.model.FailEmailModel;
import com.example.rdbrepository.FailEmailEntity;
import com.example.rdbrepository.FailEmailEntityRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@AllArgsConstructor
public class FailEmailOutConnector {

    private final FailEmailEntityRepository failEmailEntityRepository;

    public List<FailEmailModel> findUnresolved() {
        return failEmailEntityRepository.findByResolvedFalse().stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    public void saveAll(List<FailEmailModel> failEmails) {
        List<FailEmailEntity> entities = failEmails.stream()
                .map(model -> FailEmailEntity.builder()
                        .id(model.getId())
                        .toEmail(model.getToEmail())
                        .subject(model.getSubject())
                        .content(model.getContent())
                        .resolved(model.isResolved())
                        .createdAt(model.getCreatedAt())
                        .build())
                .toList();
        failEmailEntityRepository.saveAll(entities);
    }


    public FailEmailModel createFailEmail(FailEmailModel failEmailModel){
        FailEmailEntity failEmailEntity = FailEmailEntity
                .builder()
                .id(failEmailModel.getId())
                .content(failEmailModel.getContent())
                .resolved(failEmailModel.isResolved())
                .toEmail(failEmailModel.getToEmail())
                .subject(failEmailModel.getSubject())
                .createdAt(failEmailModel.getCreatedAt())
                .build();
        return toModel(failEmailEntityRepository.save(failEmailEntity));
    }

    private FailEmailModel toModel(FailEmailEntity failEmailEntity) {
        return FailEmailModel
                .builder()
                .id(failEmailEntity.getId())
                .toEmail(failEmailEntity.getToEmail())
                .content(failEmailEntity.getContent())
                .resolved(failEmailEntity.isResolved())
                .subject(failEmailEntity.getSubject())
                .createdAt(failEmailEntity.getCreatedAt())
                .build();
    }
}
