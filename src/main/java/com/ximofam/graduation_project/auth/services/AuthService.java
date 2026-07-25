package com.ximofam.graduation_project.auth.services;

import com.ximofam.graduation_project.auth.dtos.request.LoginRequest;
import com.ximofam.graduation_project.auth.dtos.request.RegisterUserRequest;
import com.ximofam.graduation_project.auth.dtos.response.TokenResponse;
import com.ximofam.graduation_project.auth.securities.CustomUserDetails;
import com.ximofam.graduation_project.common.exceptions.http.ConflictException;
import com.ximofam.graduation_project.common.exceptions.http.UnauthorizedException;
import com.ximofam.graduation_project.common.utils.Utils;
import com.ximofam.graduation_project.users.UserMapper;
import com.ximofam.graduation_project.users.dtos.response.UserResponse;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.enums.UserRole;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final TokenService tokenService;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsernameOrEmail(), request.getPassword())
        );

        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();

        return tokenService.generateTokens(
                Objects.requireNonNull(customUserDetails).getUserId(),
                customUserDetails.getUserRole().name());
    }

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

    public String registerGuest() {
        User guest = createGuestUser();
        return tokenService.generateGuestToken(guest.getId());
    }

    public TokenResponse loginGuest(String guestToken) {
        Claims claims = tokenService.verifyAndParseToken(guestToken, "guest");

        Long guestId = tokenService.extractUserId(claims);
        User guest = userRepository.findById(guestId)
                .orElseThrow(() -> new UnauthorizedException(
                        "Không thể xác thực tài khoản khách vào lúc này, vui lòng thử lại sau."));

        if (guest.getRole() != UserRole.GUEST) {
            throw new UnauthorizedException(
                    "Tài khoản này đã được nâng cấp lên tài khoản người dùng, vui lòng đăng nhập bằng tài khoản của bạn.");
        }

        return tokenService.generateTokens(guest.getId(), guest.getRole().name());
    }

    private User createGuestUser() {
        int maxAttempts = 5;
        for (int i = 0; i < maxAttempts; i++) {
            String username = "guest_" + Utils.generateRandomString(8);
            if (!userRepository.existsByUsername(username)) {
                User guest = new User();
                guest.setRole(UserRole.GUEST);
                guest.setUsername(username);
                return userRepository.save(guest);
            }
        }
        throw new RuntimeException("Không thể tạo tài khoản khách vào lúc này, vui lòng thử lại sau.");
    }
}
