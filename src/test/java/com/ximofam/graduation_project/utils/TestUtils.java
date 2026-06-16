package com.ximofam.graduation_project.utils;

import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.entities.UserProfile;
import com.ximofam.graduation_project.users.entities.enums.UserRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;

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
        user.setProfile(new UserProfile());
        return user;
    }

    public static String generateExpiredToken(Object subject, String jwtSecret) {
        Date pastExpirationDate = new Date(System.currentTimeMillis() - 1000 * 60 * 60);

        return Jwts.builder()
                .subject(subject.toString())
                .issuedAt(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 2))
                .expiration(pastExpirationDate)
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)))
                .compact();
    }
}
