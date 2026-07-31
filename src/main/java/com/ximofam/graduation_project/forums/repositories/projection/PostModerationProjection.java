package com.ximofam.graduation_project.forums.repositories.projection;

import java.util.UUID;

import com.ximofam.graduation_project.forums.enums.PostStatus;

public interface PostModerationProjection {
    UUID getId();

    String getTitle();

    UUID getAuthorId();

    PostStatus getStatus();
}
