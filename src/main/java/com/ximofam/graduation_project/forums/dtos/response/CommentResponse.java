package com.ximofam.graduation_project.forums.dtos.response;

import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class CommentResponse {
    private Long id;
    private String content;
    private Long parentId;
    private int replyCount;
    private int likeCount;
    private Instant createdAt;
    private UserSimpleResponse author;
}
