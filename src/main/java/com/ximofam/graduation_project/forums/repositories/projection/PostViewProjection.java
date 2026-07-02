package com.ximofam.graduation_project.forums.repositories.projection;

import com.ximofam.graduation_project.forums.entities.Post;

public interface PostViewProjection {
    Post getPost();

    long getLikeCount();

    long getCommentCount();
}
