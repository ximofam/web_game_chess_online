package com.ximofam.graduation_project.integration.auth;

import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.entities.UserProfile;
import com.ximofam.graduation_project.users.entities.enums.UserRole;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiLoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;


    @BeforeEach
    void setUp() {
        String encodedPassword = passwordEncoder.encode("valid_password");

        User validUser = new User();
        validUser.setEmail("valid_user@example.com");
        validUser.setUsername("valid_user");
        validUser.setPasswordHash(encodedPassword);
        validUser.setActive(true);
        validUser.setLocked(false);
        validUser.setRole(UserRole.USER);
        validUser.setProfile(new UserProfile());

        User disabledUser = new User();
        disabledUser.setEmail("disabled_user@example.com");
        disabledUser.setUsername("disabled_user");
        disabledUser.setPasswordHash(encodedPassword);
        disabledUser.setActive(false);
        disabledUser.setLocked(false);
        disabledUser.setRole(UserRole.USER);
        disabledUser.setProfile(new UserProfile());

        User lockedUser = new User();
        lockedUser.setEmail("locked_user@example.com");
        lockedUser.setUsername("locked_user");
        lockedUser.setPasswordHash(encodedPassword);
        lockedUser.setActive(true);
        lockedUser.setLocked(true);
        lockedUser.setRole(UserRole.USER);
        lockedUser.setProfile(new UserProfile());

        userRepository.saveAll(List.of(validUser, disabledUser, lockedUser));
    }

    @Test
    void shouldReturn401_WhenLoginWithInvalidCredentials() throws Exception {
        String loginPayload = """
                {
                    "usernameOrEmail": "wrong_user",
                    "password": "wrong_password"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401_WhenAccountIsDisabled() throws Exception {
        String loginPayload = """
                {
                    "usernameOrEmail": "disabled_user@example.com",
                    "password": "valid_password"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401_WhenAccountIsLocked() throws Exception {
        String loginPayload = """
                {
                    "usernameOrEmail": "locked_user@example.com",
                    "password": "valid_password"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn200AndTokens_WhenLoginSuccess() throws Exception {
        String loginPayload = """
                {
                    "usernameOrEmail": "valid_user@example.com",
                    "password": "valid_password"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }
}