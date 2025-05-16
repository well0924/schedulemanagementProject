package com.example.controller.notification;


import com.example.apimodel.notification.NotificationApiModel;
import com.example.inbound.notification.NotificationInConnector;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/notice")
public class NotificationController {

    private final NotificationInConnector notification;

    //알림 전체 목록(최신순)
    @GetMapping("/{id}")
    public List<NotificationApiModel.NotificationResponse> getNotificationsByUserId(@PathVariable("id")Long userId){
        return notification.getNotificationsByUserId(userId);
    }

    //회원의 읽지않은 알림 목록
    @GetMapping("/unread/{id}")
    public List<NotificationApiModel.NotificationResponse> getUnreadNotificationsByUserId(@PathVariable("id") Long userId) {
        return notification.getUnreadNotificationsByUserId(userId);
    }

    //알림 목록을 읽기.
    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markedRead(@PathVariable("id")Long id) {
        notification.markedRead(id);
    }
}
