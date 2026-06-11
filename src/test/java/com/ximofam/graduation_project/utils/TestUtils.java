package com.ximofam.graduation_project.utils;

import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.entities.enums.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;

public class TestUtils {

    public static User buildUser(String username, String email, String rawPassword,
                                 boolean active, PasswordEncoder passwordEncoder) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setActive(active);
        user.setLocked(false);
        user.setRole(UserRole.USER);
        return user;
    }
}
