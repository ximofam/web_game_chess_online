package com.ximofam.graduation_project.users.services;

import com.cloudinary.utils.ObjectUtils;
import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.common.helpers.dtos.CloudinaryUploadResult;
import com.ximofam.graduation_project.common.helpers.services.CloudinaryService;
import com.ximofam.graduation_project.users.UserMapper;
import com.ximofam.graduation_project.users.dtos.request.UpdateUserProfileRequest;
import com.ximofam.graduation_project.users.dtos.response.UserDetailResponse;
import com.ximofam.graduation_project.users.dtos.response.UserResponse;
import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.entities.UserProfile;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import com.ximofam.graduation_project.users.repositories.projections.UserSimpleProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final String USER_NOT_FOUND_MSG = "UserId %d không tồn tại";
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final CloudinaryService cloudinaryService;

    @Cacheable(value = "userSimple", key = "#id")
    public UserSimpleResponse getUserSimpleResponseById(Long id) {
        UserSimpleProjection p = userRepository.findSimpleById(id)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MSG, id));
        return userMapper.toUserSimpleResponse(p);
    }

    public Map<Long, UserSimpleResponse> getUsersSimpleResponseByIds(Collection<Long> ids) {
        return userRepository.findSimpleByIdIn(ids).stream()
                .collect(Collectors.toMap(UserSimpleProjection::getId, userMapper::toUserSimpleResponse));
    }

    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Username %s không tồn tại", username));

        return userMapper.toUserResponse(user);
    }

    public UserDetailResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MSG, id));

        return userMapper.toUserDetailResponse(user);
    }

    @CacheEvict(value = "userSimple", key = "#userId")
    @Transactional
    public UserDetailResponse updateUserProfile(Long userId, UpdateUserProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MSG, userId));

        UserProfile profile = user.getProfile();
        userMapper.updateUserProfile(request, profile);

        return userMapper.toUserDetailResponse(user);
    }

    @CacheEvict(value = "userSimple", key = "#userId")
    @Transactional
    public CloudinaryUploadResult uploadAvatar(Long userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MSG, userId));
        UserProfile profile = user.getProfile();
        String oldPublicId = profile.getAvatarPublicId();
        String publicId = String.format("%s_%s", user.getUsername(), UUID.randomUUID());

        CloudinaryUploadResult result = cloudinaryService.upload(
                file,
                ObjectUtils.asMap(
                        "folder", "users/avatars",
                        "public_id", publicId,
                        "resource_type", "image"
                )
        );

        profile.setAvatarPublicId(result.getPublicId());
        profile.setAvatarUrl(result.getSecureUrl());

        if (oldPublicId != null) {
            cloudinaryService.deleteAsync(oldPublicId);
        }

        return result;
    }
}
