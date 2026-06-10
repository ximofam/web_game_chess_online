package com.ximofam.graduation_project.users.services;

import com.cloudinary.utils.ObjectUtils;
import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.common.helpers.dtos.CloudinaryUploadResult;
import com.ximofam.graduation_project.common.helpers.services.CloudinaryService;
import com.ximofam.graduation_project.users.UserMapper;
import com.ximofam.graduation_project.users.dtos.request.UpdateUserRequest;
import com.ximofam.graduation_project.users.dtos.response.UserDetailResponse;
import com.ximofam.graduation_project.users.dtos.response.UserResponse;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final CloudinaryService cloudinaryService;

    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Username %s không tồn tại", username));

        return userMapper.toUserResponse(user);
    }

    public UserDetailResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("UserId %d không tồn tại", id));

        return userMapper.toUserDetailResponse(user);
    }

    @Transactional
    public UserDetailResponse updateUser(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("UserId %d không tồn tại", userId));

        userMapper.updateUser(request, user);

        return userMapper.toUserDetailResponse(user);
    }

    @Transactional
    public CloudinaryUploadResult uploadAvatar(Long userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new NotFoundException("UserId %d không tồn tại", userId));

        String oldPublicId = user.getAvatarPublicId();
        try {
            String publicId = String.format("%s_%s", user.getUsername(), UUID.randomUUID());

            CloudinaryUploadResult result = cloudinaryService.upload(
                    file,
                    ObjectUtils.asMap(
                            "folder", "users/avatars",
                            "public_id", publicId,
                            "resource_type", "image"
                    )
            );

            user.setAvatarPublicId(result.getPublicId());

            if (oldPublicId != null) {
                cloudinaryService.deleteAsync(oldPublicId);
            }

            return result;
        } catch (IOException e) {
            throw new RuntimeException("Upload avatar thất bại: " + e.getMessage(), e);
        }
    }
}
