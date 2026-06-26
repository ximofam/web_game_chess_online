package com.ximofam.graduation_project.integration.base;

import com.ximofam.graduation_project.users.dtos.request.LoginRequest;
import com.ximofam.graduation_project.users.dtos.response.TokenResponse;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class BaseIntegrationTest extends AbstractIntegrationTest {
    protected User validUser;
    protected final String PASSWORD = "valid_password";

    @BeforeEach
    void setUpBaseData() {
        validUser = userRepository.save(
                TestUtils.buildUser("valid_user", "valid_user@example.com", PASSWORD, true, passwordEncoder)
        );
    }

    protected TokenResponse performLogin(String usernameOrEmail, String password) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail(usernameOrEmail);
        request.setPassword(password);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(body, TokenResponse.class);
    }

}
