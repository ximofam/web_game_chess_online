package com.ximofam.graduation_project.admin.controllers;


import com.ximofam.graduation_project.common.securities.CustomUserDetails;
import com.ximofam.graduation_project.users.UserMapper;
import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {AdminHomeController.class})
@RequiredArgsConstructor
public class AdminControllerAdvice {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @ModelAttribute("currentUser")
    public UserSimpleResponse currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        if (!(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return null;
        }

        User user = userRepository.findById(userDetails.getUserId()).orElse(null);

        return userMapper.toUserSimpleResponse(user);
    }
}
