package com.ximofam.graduation_project.forums.dtos.response;

import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostSimpleResponse {
    private Long id;
    private UserSimpleResponse author;
    private String title;
    private long viewCount;
    private long likeCount;
}
