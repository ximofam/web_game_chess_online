package com.ximofam.graduation_project.users.services;

import com.ximofam.graduation_project.common.exceptions.http.ConflictException;
import com.ximofam.graduation_project.users.UserMapper;
import com.ximofam.graduation_project.users.dtos.request.RegisterUserRequest;
import com.ximofam.graduation_project.users.dtos.response.UserResponse;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.entities.enums.UserRole;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserResponse registerUser(RegisterUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username %s đã tồn tại", request.getUsername());
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email %s đã tồn tại", request.getEmail());
        }

        User user = userMapper.toUser(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.USER);
        user = userRepository.save(user);

        return userMapper.toUserResponse(user);
    }
}
