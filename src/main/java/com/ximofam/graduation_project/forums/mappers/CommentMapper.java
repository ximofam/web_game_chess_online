package com.ximofam.graduation_project.forums.mappers;

import com.ximofam.graduation_project.forums.dtos.response.CommentResponse;
import com.ximofam.graduation_project.forums.entities.Comment;
import com.ximofam.graduation_project.users.UserMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface CommentMapper {

    @Mapping(target = "parentId", source = "parent.id")
    CommentResponse toCommentResponse(Comment comment);
}
