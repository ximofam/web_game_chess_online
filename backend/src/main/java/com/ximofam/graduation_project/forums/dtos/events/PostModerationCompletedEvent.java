package com.ximofam.graduation_project.forums.dtos.events;

import java.util.UUID;

import com.ximofam.graduation_project.forums.enums.PostStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostModerationCompletedEvent {
    private UUID recipientId;
    private String postTitle;
    private PostStatus status;
    private String reason;
}