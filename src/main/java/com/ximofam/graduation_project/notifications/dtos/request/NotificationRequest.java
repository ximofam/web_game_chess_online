package com.ximofam.graduation_project.notifications.dtos.request;

import com.ximofam.graduation_project.notifications.entities.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class NotificationRequest {
    private Long recipientId;
    private Long senderId;
    private NotificationType type;
    private String title;
    private String message;
    private Map<String, Object> metadata;
}
