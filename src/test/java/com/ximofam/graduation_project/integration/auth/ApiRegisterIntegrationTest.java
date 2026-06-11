package com.ximofam.graduation_project.integration.auth;

import com.ximofam.graduation_project.users.entities.User;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiRegisterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String REGISTER_API_URL = "/api/auth/register";

    @BeforeEach
    void setUp() {
        User existingUser = new User();
        existingUser.setUsername("existing_user");
        existingUser.setEmail("existing@example.com");
        existingUser.setPasswordHash(passwordEncoder.encode("secret123"));
        existingUser.setRole(UserRole.USER);
        existingUser.setActive(true);

        userRepository.save(existingUser);
    }

    @Test
    void shouldReturn201_WhenRegistrationIsSuccessful() throws Exception {
        String payload = """
                {
                    "username": "new_user123",
                    "email": "newuser@example.com",
                    "password": "strong_password"
                }
                """;

        mockMvc.perform(post(REGISTER_API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new_user123"))
                .andExpect(jsonPath("$.email").value("newuser@example.com"));
    }

    @Test
    void shouldReturn409_WhenUsernameAlreadyExists() throws Exception {
        String payload = """
                {
                    "username": "existing_user",
                    "email": "another_email@example.com",
                    "password": "strong_password"
                }
                """;

        mockMvc.perform(post(REGISTER_API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn409_WhenEmailAlreadyExists() throws Exception {
        String payload = """
                {
                    "username": "another_user",
                    "email": "existing@example.com",
                    "password": "strong_password"
                }
                """;

        mockMvc.perform(post(REGISTER_API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn400_WhenEmailIsInvalid() throws Exception {
        String payload = """
                {
                    "username": "valid_user",
                    "email": "not-an-email",
                    "password": "strong_password"
                }
                """;

        mockMvc.perform(post(REGISTER_API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400_WhenUsernameIsTooShort() throws Exception {
        String payload = """
                {
                    "username": "ab",
                    "email": "valid@example.com",
                    "password": "strong_password"
                }
                """;

        mockMvc.perform(post(REGISTER_API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }
}