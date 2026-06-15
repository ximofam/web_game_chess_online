package com.ximofam.graduation_project.users.init;

import com.ximofam.graduation_project.common.helpers.utils.Utils;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.entities.UserProfile;
import com.ximofam.graduation_project.users.entities.enums.UserRole;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SuperUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.superuser.email}")
    private String email;
    @Value("${app.superuser.username}")
    private String username;
    @Value("${app.superuser.password}")
    private String password;


    @Override
    public void run(ApplicationArguments args) {
        if (!Utils.hasText(email) || !Utils.hasText(username) || !Utils.hasText(password)) {
            return;
        }

        if (userRepository.existsByEmail(email)) {
            log.info("Superuser already exists, skipping");
            return;
        }

        if (userRepository.existsByUsername(username)) {
            log.info("Superuser already exists, skipping");
            return;
        }

        User superuser = new User();
        superuser.setEmail(email);
        superuser.setUsername(username);
        superuser.setPasswordHash(passwordEncoder.encode(password));
        superuser.setRole(UserRole.SUPERUSER);
        superuser.setActive(true);
        superuser.setProfile(new UserProfile());
        userRepository.save(superuser);
        log.info("Superuser account created");
    }
}