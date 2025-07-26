package com.example.outbound.notification;

import com.example.notification.model.NotificationSettingModel;
import com.example.rdbrepository.NotificationSetting;
import com.example.rdbrepository.NotificationSettingRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class NotificationSettingOutConnector {

    private final NotificationSettingRepository notificationSettingRepository;

    public NotificationSettingModel findByUserId(Long userId) {
        return toModel(notificationSettingRepository
                .findByUserId(userId)
                .orElseThrow());
    }

    public NotificationSettingModel findById(Long id) {
        return toModel(notificationSettingRepository
                .findById(id)
                .orElseThrow());
    }

    //사용자 알림 설정 조회 또는 생성
    public NotificationSettingModel getOrCreate(Long userId) {
        return notificationSettingRepository.findByUserId(userId)
                .map(this::toModel)
                .orElseGet(() -> {
                    NotificationSetting newSetting = NotificationSetting.builder()
                            .userId(userId)
                            .webEnabled(true)
                            .emailEnabled(false)
                            .pushEnabled(false)
                            .build();
                    return toModel(notificationSettingRepository.save(newSetting));
                });
    }

    //전체 설정 저장
    public void updateSetting(NotificationSettingModel notificationSettingModel) {
        notificationSettingRepository.save(toEntity(notificationSettingModel));
    }

    private NotificationSettingModel toModel(NotificationSetting entity) {
        return NotificationSettingModel.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .webEnabled(entity.isWebEnabled())
                .emailEnabled(entity.isEmailEnabled())
                .pushEnabled(entity.isPushEnabled())
                .build();
    }

    private NotificationSetting toEntity(NotificationSettingModel model) {
        return NotificationSetting
                .builder()
                .id(model.getId())
                .userId(model.getUserId())
                .webEnabled(model.isWebEnabled())
                .emailEnabled(model.isEmailEnabled())
                .pushEnabled(model.isPushEnabled())
                .build();
    }
}
