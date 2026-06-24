package com.ximofam.graduation_project.forums.dtos.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostDetailResponse {
    private Long id;
    private String title;
    private String content;
    private String status;
}
