package com.ximofam.graduation_project.forums.mappers;

import com.ximofam.graduation_project.forums.dtos.request.CreatePostRequest;
import com.ximofam.graduation_project.forums.dtos.response.PostDetailResponse;
import com.ximofam.graduation_project.forums.entities.Post;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PostMapper {
    Post toPost(CreatePostRequest request);

    PostDetailResponse toPostDetailResponse(Post post);
}
