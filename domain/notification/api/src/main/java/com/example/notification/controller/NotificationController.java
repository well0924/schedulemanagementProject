package com.example.notification.controller;

import com.example.notification.apimodel.NotificationApiModel;
import com.example.notification.inconnector.NotificationInConnector;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/notice")
public class NotificationController {

    private final NotificationInConnector notification;

    public List<NotificationApiModel.NotificationResponse> getNotificationsByUserId(@PathVariable("id")Long userId){
        return notification.getNotificationsByUserId(userId);
    }
}
