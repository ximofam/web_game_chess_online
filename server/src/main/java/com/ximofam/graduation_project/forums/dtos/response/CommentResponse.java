package com.ximofam.graduation_project.forums.dtos.response;

import java.util.UUID;

import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class CommentResponse {
    private UUID id;
    private String content;
    private UUID parentId;
    private int replyCount;
    private int likeCount;
    private Instant createdAt;
    private boolean liked;
    private UserSimpleResponse author;
}
