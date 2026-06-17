package com.ximofam.graduation_project.admin.securities;

import com.ximofam.graduation_project.common.helpers.services.JwtService;
import com.ximofam.graduation_project.common.securities.CustomUserDetails;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import com.ximofam.graduation_project.users.services.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(@NonNull HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        assert userDetails != null;
        User user = userRepository.findById(userDetails.getUserId()).orElseThrow();

        String accessToken = jwtService.generateAccessToken(user.getId(), List.of(user.getRole().name()));
        String refreshToken = refreshTokenService.generateRefreshToken(user);

        // Trả JSON về cho JS
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("""
                {"accessToken":"%s","refreshToken":"%s"}
                """.formatted(accessToken, refreshToken));
    }
}