package com.example.outbound.notification;

import com.example.interfaces.notification.push.NotificationSettingRepositoryPort;
import com.example.notification.mapper.NotificationEntityMapper;
import com.example.notification.mapper.NotificationMapper;
import com.example.notification.model.NotificationSettingModel;
import com.example.rdbrepository.NotificationSetting;
import com.example.rdbrepository.NotificationSettingRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class NotificationSettingOutConnector implements NotificationSettingRepositoryPort {

    private final NotificationSettingRepository notificationSettingRepository;

    private final NotificationEntityMapper notificationEntityMapper;

    private final NotificationMapper notificationMapper;

    //사용자 알림 설정 조회 또는 생성
    public NotificationSettingModel getOrCreate(Long userId) {
        return notificationSettingRepository.findByUserId(userId)
                .map(notificationMapper::toModel)
                .orElseGet(() -> {
                    NotificationSetting newSetting = NotificationSetting
                            .builder()
                            .userId(userId)
                            .webEnabled(true)
                            .emailEnabled(false)
                            .pushEnabled(false)
                            .build();
                    return notificationMapper.toModel(notificationSettingRepository.save(newSetting));
                });
    }

    //전체 설정 저장
    public void updateSetting(NotificationSettingModel notificationSettingModel) {
        notificationSettingRepository.save(notificationEntityMapper
                .toEntity(notificationSettingModel));
    }

}
