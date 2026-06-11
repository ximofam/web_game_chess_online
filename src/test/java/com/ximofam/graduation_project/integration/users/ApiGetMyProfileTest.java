package com.ximofam.graduation_project.integration.users;

import com.ximofam.graduation_project.integration.base.BaseIntegrationTest;
import com.ximofam.graduation_project.users.dtos.response.TokenResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ApiGetMyProfileTest extends BaseIntegrationTest {
    private static final String API_URL = "/api/users/me";

    @Test
    @DisplayName("lấy profile với token hợp lệ → 200 + đúng thông tin user")
    void getMyProfile_withValidToken_shouldReturn200AndUserInfo() throws Exception {
        TokenResponse tokens = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(get(API_URL)
                        .header("Authorization", String.format("Bearer %s", tokens.getAccessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(validUser.getUsername()))
                .andExpect(jsonPath("$.email").value(validUser.getEmail()))
                .andExpect(jsonPath("$.role").value(validUser.getRole().name()));
    }

    @Test
    @DisplayName("lấy profile không có token → 401")
    void getMyProfile_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get(API_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("lấy profile với token giả mạo → 401")
    void getMyProfile_withInvalidToken_shouldReturn401() throws Exception {
        mockMvc.perform(get(API_URL)
                        .header("Authorization", "Bearer this.is.fake.token"))
                .andExpect(status().isUnauthorized());
    }

//    @Test
//    @DisplayName("lấy profile với token hết hạn → 401")
//    void getMyProfile_withExpiredToken_shouldReturn401() throws Exception {
//        mockMvc.perform(get(API_URL)
//                        .header("Authorization", "Bearer "))
//                .andExpect(status().isUnauthorized());
//    }
}
