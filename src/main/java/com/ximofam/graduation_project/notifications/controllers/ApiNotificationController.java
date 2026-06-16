package com.ximofam.graduation_project.notifications.controllers;

import com.ximofam.graduation_project.notifications.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class ApiNotificationController {
    private final NotificationService notificationService;
}
