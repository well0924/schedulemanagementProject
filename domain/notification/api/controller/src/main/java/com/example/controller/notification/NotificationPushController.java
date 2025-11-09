package com.example.controller.notification;

import com.example.apimodel.notification.NotificationPushApiModel;
import com.example.interfaces.notification.push.NotificationPushInterfaces;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/push")
public class NotificationPushController {

    private final NotificationPushInterfaces pushInConnector;

    //알림 푸시 구독
    @PostMapping("/subscribe")
    public ResponseEntity<NotificationPushApiModel.NotificationPushResponse> subscribe(@RequestBody NotificationPushApiModel.NotificationPushRequest request) {
        NotificationPushApiModel.NotificationPushResponse model = pushInConnector.subscribe(request);
        return ResponseEntity.ok(model);
    }

    //
    @GetMapping("/active")
    public ResponseEntity<List<NotificationPushApiModel.NotificationPushResponse>> getActive(@RequestParam Long memberId) {
        return ResponseEntity.ok(pushInConnector.getActiveSubscriptions(memberId));
    }

    // 알림 푸시 구독 해제
    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@RequestParam Long memberId, @RequestParam String endpoint) {
        pushInConnector.deactivateByEndpoint(memberId, endpoint);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/unsubscribeAll")
    public ResponseEntity<Void> unsubscribeAll(@RequestParam Long memberId) {
        pushInConnector.deactivateAll(memberId);
        return ResponseEntity.ok().build();
    }
}
