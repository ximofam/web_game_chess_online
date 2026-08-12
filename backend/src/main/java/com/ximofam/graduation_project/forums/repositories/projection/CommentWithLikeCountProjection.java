package com.ximofam.graduation_project.forums.repositories.projection;

import com.ximofam.graduation_project.forums.entities.Comment;

public interface CommentWithLikeCountProjection {
    Comment getComment();

    Long getLikeCount();
}
