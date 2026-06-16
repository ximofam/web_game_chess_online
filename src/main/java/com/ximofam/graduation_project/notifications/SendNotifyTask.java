package com.ximofam.graduation_project.notifications;

import com.ximofam.graduation_project.notifications.dtos.request.NotificationRequest;
import com.ximofam.graduation_project.notifications.entities.enums.NotificationType;
import com.ximofam.graduation_project.notifications.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class SendNotifyTask {
    private final NotificationService notificationService;

    @Scheduled(fixedRateString = "5s")
    public void execute() {
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .recipientId(1L)
                .title("Đây là tin nhắn từ hệ thống")
                .type(NotificationType.SYSTEM_MESSAGE)
                .message("Được gửi vào lúc: " + Instant.now())
                .build();

        notificationService.send(notificationRequest);
    }
}
