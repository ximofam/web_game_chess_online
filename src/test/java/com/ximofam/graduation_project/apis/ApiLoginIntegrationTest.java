package com.ximofam.graduation_project.apis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest // Khởi động toàn bộ Spring Context (bao gồm cả DB, Security, Flyway)
@AutoConfigureMockMvc // Khởi tạo MockMvc để giả lập client gọi API
@ActiveProfiles("test")
@Transactional // RẤT QUAN TRỌNG: Tự động rollback dữ liệu sau mỗi hàm test, trả DB về trạng thái sạch
class ApiLoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
}