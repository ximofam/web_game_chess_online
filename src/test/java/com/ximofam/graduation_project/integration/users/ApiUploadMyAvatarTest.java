package com.ximofam.graduation_project.integration.users;

import com.ximofam.graduation_project.common.helpers.dtos.CloudinaryUploadResult;
import com.ximofam.graduation_project.common.helpers.services.CloudinaryService;
import com.ximofam.graduation_project.integration.base.BaseIntegrationTest;
import com.ximofam.graduation_project.users.dtos.response.TokenResponse;
import com.ximofam.graduation_project.users.entities.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ApiUploadMyAvatarTest extends BaseIntegrationTest {

    private final String API_URL = "/api/users/me/avatar";

    @MockitoBean
    private CloudinaryService cloudinaryService;

    private final CloudinaryUploadResult mockResult = CloudinaryUploadResult.builder()
            .publicId("new_avatar_123")
            .secureUrl("https://res.cloudinary.com/demo/new_avatar_123.jpg")
            .build();

    @Test
    @DisplayName("Upload avatar thành công -> 200 OK + cập nhật Database")
    void uploadAvatar_Success() throws Exception {
        TokenResponse tokens = performLogin(validUser.getUsername(), PASSWORD);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "dummy_image_content".getBytes()
        );

        when(cloudinaryService.upload(any(), anyMap())).thenReturn(mockResult);


        mockMvc.perform(multipart(HttpMethod.PATCH, API_URL)
                        .file(file)
                        .header("Authorization", "Bearer " + tokens.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(mockResult.getSecureUrl()));


        User updatedUser = userRepository.findById(validUser.getId()).orElseThrow();
        Assertions.assertEquals("new_avatar_123", updatedUser.getAvatarPublicId());
    }

    @Test
    @DisplayName("User đã có avatar cũ -> Xóa avatar cũ và cập nhật mới")
    void uploadAvatar_WithOldAvatar_ShouldDeleteOldAndUploadNew() throws Exception {
        validUser.setAvatarPublicId("old_avatar_456");
        userRepository.save(validUser);

        TokenResponse tokens = performLogin(validUser.getUsername(), PASSWORD);
        MockMultipartFile file = new MockMultipartFile(
                "file", "new_avatar.jpg", MediaType.IMAGE_JPEG_VALUE, "dummy".getBytes());


        when(cloudinaryService.upload(any(), anyMap())).thenReturn(mockResult);

        mockMvc.perform(multipart(HttpMethod.PATCH, API_URL)
                        .file(file)
                        .header("Authorization", "Bearer " + tokens.getAccessToken()))
                .andExpect(status().isOk());

        User updatedUser = userRepository.findById(validUser.getId()).orElseThrow();
        Assertions.assertEquals(mockResult.getPublicId(), updatedUser.getAvatarPublicId());

        verify(cloudinaryService, timeout(1000).times(1)).deleteAsync("old_avatar_456");
    }

    @Test
    @DisplayName("Upload thất bại do Cloudinary lỗi -> 500 Internal Server Error")
    void uploadAvatar_CloudinaryFails_ShouldReturn500() throws Exception {
        TokenResponse tokens = performLogin(validUser.getUsername(), PASSWORD);
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", MediaType.IMAGE_JPEG_VALUE, "dummy".getBytes());

        when(cloudinaryService.upload(any(), anyMap())).thenThrow(new IOException("Cloudinary is down"));

        mockMvc.perform(multipart(HttpMethod.PATCH, API_URL)
                        .file(file)
                        .header("Authorization", "Bearer " + tokens.getAccessToken()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Không có token -> 401 Unauthorized")
    void uploadAvatar_WithoutToken_ShouldReturn401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", MediaType.IMAGE_JPEG_VALUE, "dummy".getBytes());

        mockMvc.perform(multipart(HttpMethod.PATCH, API_URL)
                        .file(file))
                .andExpect(status().isUnauthorized());
    }
}