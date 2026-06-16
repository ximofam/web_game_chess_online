package com.ximofam.graduation_project.users.controllers;

import com.ximofam.graduation_project.common.helpers.dtos.ApiResponse;
import com.ximofam.graduation_project.common.helpers.dtos.CloudinaryUploadResult;
import com.ximofam.graduation_project.users.dtos.request.UpdateUserProfileRequest;
import com.ximofam.graduation_project.users.dtos.response.UserDetailResponse;
import com.ximofam.graduation_project.users.dtos.response.UserResponse;
import com.ximofam.graduation_project.users.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class ApiUserController {
    private final UserService userService;

    @GetMapping("/{username}")
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUserByUsername(username));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDetailResponse> getMyProfile(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserDetailResponse> updateMyProfile(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid UpdateUserProfileRequest request) {

        return ResponseEntity.ok(userService.updateUserProfile(userId, request));
    }

    @PatchMapping("/me/avatar")
    public ResponseEntity<ApiResponse> uploadMyAvatar(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file) {

        CloudinaryUploadResult result = userService.uploadAvatar(userId, file);
        ApiResponse apiResponse = ApiResponse.builder()
                .message("Đã upload thành công avatar cho user")
                .data(Map.of("avatarUrl", result.getSecureUrl()))
                .build();

        return ResponseEntity.ok(apiResponse);
    }
}
