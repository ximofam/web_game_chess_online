package com.ximofam.graduation_project.forums.dtos.response;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostDetailResponse {
    private UUID id;
    private String title;
    private String content;
    private String status;
    private long viewCount;
    private long likeCount;
    private long commentCount;
    private ApprovalInfoResponse approvalInfo;
}
