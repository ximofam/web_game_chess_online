package com.ximofam.graduation_project.integration.users;

import com.ximofam.graduation_project.integration.base.BaseIntegrationTest;
import com.ximofam.graduation_project.users.dtos.response.TokenResponse;
import com.ximofam.graduation_project.utils.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ApiGetUserByUsernameTest extends BaseIntegrationTest {
    private static final String API_URL = "/api/users/";
    @Value("${app.security.jwt.secret-key}")
    private String jwtSecret;

    @Test
    @DisplayName("lấy user với token hợp lệ → 200 + đúng thông tin user")
    void getUser_withValidToken_shouldReturn200AndUserInfo() throws Exception {
        TokenResponse tokens = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(get(API_URL + validUser.getUsername())
                        .header("Authorization", String.format("Bearer %s", tokens.getAccessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(validUser.getUsername()))
                .andExpect(jsonPath("$.email").value(validUser.getEmail()))
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    @DisplayName("lấy user không có token → 401")
    void getMyProfile_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get(API_URL + validUser.getUsername()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("lấy user với token giả mạo → 401")
    void getMyProfile_withInvalidToken_shouldReturn401() throws Exception {
        mockMvc.perform(get(API_URL + validUser.getUsername())
                        .header("Authorization", "Bearer this.is.fake.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("lấy profile với token hết hạn → 401")
    void getMyProfile_withExpiredToken_shouldReturn401() throws Exception {
        String expiredToken = TestUtils.generateExpiredToken(validUser.getId(), jwtSecret);

        mockMvc.perform(get(API_URL + validUser.getUsername())
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }
}
