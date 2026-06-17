package com.ximofam.graduation_project.notifications.dtos.response;

import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
public class NotificationResponse {
    private Long id;
    private UserSimpleResponse sender;
    private String type;
    private String title;
    private String message;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private boolean isRead;
}
