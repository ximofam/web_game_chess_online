package com.ximofam.graduation_project.integration.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ximofam.graduation_project.users.dtos.request.LoginRequest;
import com.ximofam.graduation_project.users.dtos.response.TokenResponse;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import com.ximofam.graduation_project.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public abstract class BaseIntegrationTest {
    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    protected final ObjectMapper objectMapper = new ObjectMapper();
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