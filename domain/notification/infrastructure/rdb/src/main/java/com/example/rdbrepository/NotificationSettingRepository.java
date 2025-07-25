package com.example.rdbrepository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting,Long> {

    Optional<NotificationSetting> findByUserId(String userId);
}
