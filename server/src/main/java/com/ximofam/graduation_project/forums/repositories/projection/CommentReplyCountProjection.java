package com.ximofam.graduation_project.forums.repositories.projection;

import java.util.UUID;

public interface CommentReplyCountProjection {
    UUID getCommentId();

    Long getReplyCount();
}
