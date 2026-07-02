package com.ximofam.graduation_project.forums.mappers;

import com.ximofam.graduation_project.forums.dtos.request.CreatePostRequest;
import com.ximofam.graduation_project.forums.dtos.response.PostDetailResponse;
import com.ximofam.graduation_project.forums.dtos.response.PostResponse;
import com.ximofam.graduation_project.forums.dtos.response.PostSimpleResponse;
import com.ximofam.graduation_project.forums.entities.Post;
import com.ximofam.graduation_project.forums.repositories.projection.PostSimpleProjection;
import com.ximofam.graduation_project.users.UserMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface PostMapper {
    Post toPost(CreatePostRequest request);

    PostDetailResponse toPostDetailResponse(Post post);

    PostResponse toPostResponse(Post post);

    @Mapping(target = "author.id", source = "p.authorId")
    @Mapping(target = "author.username", source = "p.authorUsername")
    @Mapping(target = "author.avatarUrl", source = "p.authorAvatarUrl")
    PostSimpleResponse toPostSimpleResponse(PostSimpleProjection p, boolean liked);
}
