package com.ximofam.graduation_project.forums.dtos.response;

import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class PostResponse {
    private Long id;
    private UserSimpleResponse author;
    private String title;
    private String content;
    private long viewCount;
    private long likeCount;
    private long commentCount;
    private Instant createdAt;
    private boolean likedByUser;
}
