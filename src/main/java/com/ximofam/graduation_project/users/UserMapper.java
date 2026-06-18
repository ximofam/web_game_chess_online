package com.ximofam.graduation_project.users;

import com.ximofam.graduation_project.users.dtos.request.RegisterUserRequest;
import com.ximofam.graduation_project.users.dtos.request.UpdateUserProfileRequest;
import com.ximofam.graduation_project.users.dtos.response.UserDetailResponse;
import com.ximofam.graduation_project.users.dtos.response.UserResponse;
import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.entities.UserProfile;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toUser(RegisterUserRequest request);

    @Mapping(target = "avatarUrl", source = "profile.avatarUrl")
    UserResponse toUserResponse(User user);

    @Mapping(target = "avatarUrl", source = "profile.avatarUrl")
    UserDetailResponse toUserDetailResponse(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUserProfile(UpdateUserProfileRequest request, @MappingTarget UserProfile userProfile);

    @Mapping(target = "avatarUrl", source = "profile.avatarUrl")
    UserSimpleResponse toUserSimpleResponse(User user);
}