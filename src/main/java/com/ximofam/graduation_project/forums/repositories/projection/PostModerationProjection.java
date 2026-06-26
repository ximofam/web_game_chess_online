package com.ximofam.graduation_project.forums.repositories.projection;

import com.ximofam.graduation_project.forums.entities.enums.PostStatus;

public interface PostModerationProjection {
    Long getId();

    String getTitle();

    Long getAuthorId();

    PostStatus getStatus();
}
