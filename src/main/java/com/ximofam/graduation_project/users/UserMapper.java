package com.ximofam.graduation_project.users;

import com.ximofam.graduation_project.common.helpers.services.CloudinaryService;
import com.ximofam.graduation_project.users.dtos.request.RegisterUserRequest;
import com.ximofam.graduation_project.users.dtos.request.UpdateUserProfileRequest;
import com.ximofam.graduation_project.users.dtos.response.UserDetailResponse;
import com.ximofam.graduation_project.users.dtos.response.UserResponse;
import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.entities.UserProfile;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class UserMapper {
    @Autowired
    protected CloudinaryService cloudinaryService;

    public abstract User toUser(RegisterUserRequest request);

    @Mapping(target = "avatarUrl", source = "avatarPublicId", qualifiedByName = "buildAvatarUrl")
    public abstract UserResponse toUserResponse(User user);

    @Mapping(target = "avatarUrl", source = "avatarPublicId", qualifiedByName = "buildAvatarUrl")
    public abstract UserDetailResponse toUserDetailResponse(User user);

    @Named("buildAvatarUrl")
    protected String buildAvatarUrl(String publicId) {
        if (publicId == null) return null;
        return cloudinaryService.getUrl(publicId);
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void updateUserProfile(UpdateUserProfileRequest request, @MappingTarget UserProfile userProfile);

    @Mapping(target = "avatarUrl", source = "avatarPublicId", qualifiedByName = "buildAvatarUrl")
    public abstract UserSimpleResponse toUserSimpleResponse(User user);
}