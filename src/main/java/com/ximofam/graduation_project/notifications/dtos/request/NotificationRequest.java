package com.ximofam.graduation_project.notifications.dtos.request;

import com.ximofam.graduation_project.notifications.entities.enums.NotificationType;
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
    private Long recipientId;
    private Long senderId;
    private NotificationType type;
    private String title;
    private String message;
    private Map<String, Object> metadata;
}
