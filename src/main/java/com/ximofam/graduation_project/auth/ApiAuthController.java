package com.ximofam.graduation_project.auth;

import com.ximofam.graduation_project.auth.dtos.request.LoginRequest;
import com.ximofam.graduation_project.auth.dtos.request.RegisterUserRequest;
import com.ximofam.graduation_project.auth.dtos.response.TokenResponse;
import com.ximofam.graduation_project.auth.services.AuthService;
import com.ximofam.graduation_project.auth.services.TokenService;
import com.ximofam.graduation_project.common.utils.Utils;
import com.ximofam.graduation_project.users.dtos.response.UserResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class ApiAuthController {
    private final AuthService authService;
    private final TokenService tokenService;
    @Value("${app.user.guest-max-age-days}")
    private int guestMaxAgeDays;

    private static final String GUEST_COOKIE_NAME = "guestToken";
    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";
    private static final String REFRESH_COOKIE_SAME_SITE = "Strict";

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody @Valid RegisterUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerUser(request));
    }

    @PostMapping("/register/guest")
    public ResponseEntity<Void> registerGuest(
            @CookieValue(name = GUEST_COOKIE_NAME, required = false) String guestToken,
            HttpServletResponse response) {

        if (isValidGuestToken(guestToken)) {
            return ResponseEntity.ok().build();
        }

        String newGuestToken = authService.registerGuest();
        Utils.addCookie(response, GUEST_COOKIE_NAME, newGuestToken, Duration.ofDays(guestMaxAgeDays));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login/guest")
    public ResponseEntity<TokenResponse> loginGuest(
            @CookieValue(name = GUEST_COOKIE_NAME) String guestToken,
            HttpServletResponse response) {

        TokenResponse tokens = authService.loginGuest(guestToken);
        setRefreshTokenCookie(response, tokens.getRefreshToken());

        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest request, HttpServletResponse response) {
        TokenResponse tokens = authService.login(request);
        setRefreshTokenCookie(response, tokens.getRefreshToken());
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/refresh/guest-token")
    public ResponseEntity<Void> refreshQuestToken(
            @CookieValue(name = GUEST_COOKIE_NAME) String guestToken,
            HttpServletResponse response) {

        Claims claims = tokenService.verifyAndParseToken(guestToken, "guest");

        Long guestId = tokenService.extractUserId(claims);
        String newGuestToken = tokenService.generateGuestToken(guestId);
        Utils.addCookie(response, GUEST_COOKIE_NAME, newGuestToken, Duration.ofDays(guestMaxAgeDays));

        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(
            @CookieValue(name = REFRESH_COOKIE_NAME) String refreshToken,
            HttpServletResponse response) {
        TokenResponse tokens = tokenService.refresh(refreshToken);
        setRefreshTokenCookie(response, tokens.getRefreshToken());
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null) {
            tokenService.deleteRefreshSession(refreshToken);
        }
        Utils.clearCookie(response, REFRESH_COOKIE_NAME, REFRESH_COOKIE_PATH, REFRESH_COOKIE_SAME_SITE, false);
        return ResponseEntity.ok("Logged out successfully");
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Utils.addCookie(
                response,
                REFRESH_COOKIE_NAME,
                refreshToken,
                Duration.ofDays(tokenService.getRefreshTokenExpDays()),
                REFRESH_COOKIE_PATH,
                REFRESH_COOKIE_SAME_SITE,
                false
        );
    }

    private boolean isValidGuestToken(String guestToken) {
        if (guestToken == null) {
            return false;
        }
        try {
            tokenService.verifyAndParseToken(guestToken, "guest");
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}