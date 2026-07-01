package com.ximofam.graduation_project.users.services;

import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.common.exceptions.http.UnauthorizedException;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

@Service
@RequestScope
@RequiredArgsConstructor
public class UserCurrentService {

    private final UserRepository userRepository;
    private User cachedUser;

    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }

        return (Long) auth.getPrincipal();
    }

    public User getCurrentUser() {
        if (cachedUser == null) {
            cachedUser = userRepository.findById(getCurrentUserId())
                    .orElseThrow(() -> new NotFoundException("Current user not found"));
        }

        return cachedUser;
    }

    public User getReferenceUser() {
        return userRepository.getReferenceById(getCurrentUserId());
    }
}