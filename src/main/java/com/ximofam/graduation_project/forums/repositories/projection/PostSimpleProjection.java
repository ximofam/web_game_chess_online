package com.ximofam.graduation_project.forums.repositories.projection;

import java.time.Instant;

public interface PostSimpleProjection {
    Long getId();

    Long getAuthorId();

    String getAuthorUsername();

    String getAuthorAvatarUrl();

    String getTitle();

    long getViewCount();

    long getLikeCount();

    Instant getCreatedAt();
}
