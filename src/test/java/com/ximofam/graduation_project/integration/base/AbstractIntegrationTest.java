package com.ximofam.graduation_project.integration.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public abstract class AbstractIntegrationTest extends AbstractSpringBootTest {
    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    protected final ObjectMapper objectMapper = new ObjectMapper();
}
