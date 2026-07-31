package com.ximofam.graduation_project.forums.repositories.projection;

import java.util.UUID;

import java.time.Instant;

public interface PostSimpleProjection {
    UUID getId();

    UUID getAuthorId();

    String getAuthorUsername();

    String getAuthorAvatarUrl();

    String getTitle();

    long getViewCount();

    long getLikeCount();

    long getCommentCount();

    Instant getCreatedAt();

    // ponytail: nullable — only populated for mine=true queries, null for public listing
    String getStatus();
}
