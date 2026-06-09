package com.ximofam.graduation_project.users.services;

import com.ximofam.graduation_project.common.exceptions.http.ConflictException;
import com.ximofam.graduation_project.common.helpers.services.JwtService;
import com.ximofam.graduation_project.users.UserMapper;
import com.ximofam.graduation_project.users.dtos.request.LoginRequest;
import com.ximofam.graduation_project.users.dtos.request.RegisterUserRequest;
import com.ximofam.graduation_project.users.dtos.response.TokenResponse;
import com.ximofam.graduation_project.users.dtos.response.UserResponse;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.entities.enums.UserRole;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import com.ximofam.graduation_project.users.securities.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

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

    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsernameOrEmail(), request.getPassword())
        );
        
        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = Objects.requireNonNull(customUserDetails).getUser();

        return generateTokens(user);
    }

    private TokenResponse generateTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), List.of(user.getRole().name()));
        String refreshToken = refreshTokenService.generateRefreshToken(user);

        TokenResponse tokenRes = new TokenResponse();
        tokenRes.setRefreshToken(refreshToken);
        tokenRes.setAccessToken(accessToken);

        return tokenRes;
    }
}
