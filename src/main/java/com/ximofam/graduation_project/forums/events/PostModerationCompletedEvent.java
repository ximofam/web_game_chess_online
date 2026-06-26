package com.ximofam.graduation_project.forums.events;

import com.ximofam.graduation_project.forums.entities.enums.PostStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostModerationCompletedEvent {
    private Long recipientId;
    private String postTitle;
    private PostStatus status;
    private String reason;
}