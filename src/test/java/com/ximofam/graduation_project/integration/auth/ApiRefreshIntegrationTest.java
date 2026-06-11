package com.ximofam.graduation_project.integration.auth;

import com.ximofam.graduation_project.integration.base.BaseIntegrationTest;
import com.ximofam.graduation_project.users.dtos.request.RefreshRequest;
import com.ximofam.graduation_project.users.dtos.response.TokenResponse;
import com.ximofam.graduation_project.users.entities.RefreshToken;
import com.ximofam.graduation_project.users.repositories.RefreshTokenRepository;
import com.ximofam.graduation_project.users.services.RefreshTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


public class ApiRefreshIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private static final String REFRESH_API_URL = "/api/auth/refresh";


    @Test
    @DisplayName("refresh với token hợp lệ → 200 + accessToken mới")
    void refresh_withValidToken_shouldReturn200AndNewAccessToken() throws Exception {
        TokenResponse loginResponse = performLogin(validUser.getUsername(), PASSWORD);

        RefreshRequest refreshRequest = new RefreshRequest();
        refreshRequest.setRefreshToken(loginResponse.getRefreshToken());

        mockMvc.perform(post(REFRESH_API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }


    @Test
    @DisplayName("refresh với token giả mạo => 401")
    void refresh_withInvalidToken_shouldReturn401() throws Exception {
        RefreshRequest refreshRequest = new RefreshRequest();
        refreshRequest.setRefreshToken("this.is.totally.fake");

        mockMvc.perform(post(REFRESH_API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh sau khi token bị revoke => 401")
    void refresh_afterTokenRevoked_shouldReturn401() throws Exception {
        TokenResponse loginResponse = performLogin(validUser.getUsername(), PASSWORD);

        RefreshToken refreshToken = refreshTokenService.verifyAndGetRefreshToken(loginResponse.getRefreshToken());
        refreshToken.setIsRevoked(true);
        refreshTokenRepository.save(refreshToken);

        RefreshRequest refreshRequest = new RefreshRequest();
        refreshRequest.setRefreshToken(loginResponse.getRefreshToken());

        mockMvc.perform(post(REFRESH_API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh sau khi token hết hạn => 401")
    void refresh_afterExpired_shouldReturn401() throws Exception {
        TokenResponse loginResponse = performLogin(validUser.getUsername(), PASSWORD);

        RefreshToken refreshToken = refreshTokenService.verifyAndGetRefreshToken(loginResponse.getRefreshToken());
        refreshToken.setExpiresAt(Instant.now());
        refreshTokenRepository.save(refreshToken);

        RefreshRequest refreshRequest = new RefreshRequest();
        refreshRequest.setRefreshToken(loginResponse.getRefreshToken());

        mockMvc.perform(post(REFRESH_API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());
    }
}
