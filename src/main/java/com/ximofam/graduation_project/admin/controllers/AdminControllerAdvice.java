package com.ximofam.graduation_project.admin.controllers;


import com.ximofam.graduation_project.users.UserMapper;
import com.ximofam.graduation_project.users.dtos.response.UserDetailResponse;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.securities.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Objects;

@ControllerAdvice(assignableTypes = {AdminHomeController.class})
@RequiredArgsConstructor
public class AdminControllerAdvice {
    private final UserMapper userMapper;

    @ModelAttribute("currentUser")
    public UserDetailResponse currentUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || Objects.requireNonNull(authentication.getPrincipal()).equals("anonymousUser")) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof CustomUserDetails)) {
            return null;
        }

        User user = ((CustomUserDetails) principal).getUser();

        return userMapper.toUserDetailResponse(user);
    }
}
