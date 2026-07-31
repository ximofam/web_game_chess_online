package com.ximofam.graduation_project.notifications.dtos.request;

import java.util.UUID;

import com.ximofam.graduation_project.notifications.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    private UUID recipientId;
    private UUID senderId;
    private NotificationType type;
    private String title;
    private String message;
    private Map<String, Object> metadata;
}
