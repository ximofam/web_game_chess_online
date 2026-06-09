package com.ximofam.graduation_project.users;

import com.ximofam.graduation_project.users.dtos.request.RegisterUserRequest;
import com.ximofam.graduation_project.users.dtos.response.UserDetailResponse;
import com.ximofam.graduation_project.users.dtos.response.UserResponse;
import com.ximofam.graduation_project.users.entities.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toUser(RegisterUserRequest request);

    @Mapping(target = "fullName", source = "fullName")
    UserResponse toUserResponse(User user);

    @Mapping(target = "fullName", source = "fullName")
    UserDetailResponse toUserDetailResponse(User user);
}
