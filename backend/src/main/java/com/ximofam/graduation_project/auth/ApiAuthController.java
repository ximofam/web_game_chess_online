package com.ximofam.graduation_project.auth;

import com.ximofam.graduation_project.auth.dtos.request.LoginRequest;
import com.ximofam.graduation_project.auth.dtos.request.RefreshGuestTokenRequest;
import com.ximofam.graduation_project.auth.dtos.request.RefreshTokenRequest;
import com.ximofam.graduation_project.auth.dtos.request.RegisterUserRequest;
import com.ximofam.graduation_project.auth.dtos.response.GuestTokenResponse;
import com.ximofam.graduation_project.auth.dtos.response.TokenResponse;
import com.ximofam.graduation_project.auth.services.AuthService;
import com.ximofam.graduation_project.auth.services.TokenService;
import com.ximofam.graduation_project.auth.enums.TokenType;
import com.ximofam.graduation_project.common.exceptions.http.UnauthorizedException;
import com.ximofam.graduation_project.common.utils.CookieUtils;
import com.ximofam.graduation_project.users.dtos.response.UserResponse;
import io.jsonwebtoken.Claims;
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
    private final CookieUtils cookieUtils;

    private static final String GUEST_COOKIE_NAME = "guestToken";
    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody @Valid RegisterUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerUser(request));
    }

    @PostMapping("/register/guest")
    public ResponseEntity<GuestTokenResponse> registerGuest(HttpServletResponse response) {
        String newGuestToken = authService.registerGuest();
        cookieUtils.addCookie(response, GUEST_COOKIE_NAME, newGuestToken, Duration.ofDays(guestMaxAgeDays));
        return ResponseEntity.status(HttpStatus.CREATED).body(new GuestTokenResponse(newGuestToken));
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
    public ResponseEntity<GuestTokenResponse> refreshQuestToken(
            @CookieValue(name = GUEST_COOKIE_NAME, required = false) String cookieGuestToken,
            @RequestBody(required = false) RefreshGuestTokenRequest request,
            HttpServletResponse response) {

        String guestToken = cookieGuestToken;
        if (guestToken == null && request != null) {
            guestToken = request.getGuestToken();
        }
        
        if (guestToken == null) {
            throw new UnauthorizedException("Guest token is required");
        }

        Claims claims = tokenService.verifyAndParseToken(guestToken, TokenType.GUEST);

        String guestId = tokenService.extractUserId(claims);
        String newGuestToken = tokenService.generateGuestToken(guestId);
        cookieUtils.addCookie(response, GUEST_COOKIE_NAME, newGuestToken, Duration.ofDays(guestMaxAgeDays));

        return ResponseEntity.ok(new GuestTokenResponse(newGuestToken));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String cookieRefreshToken,
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletResponse response) {
            
        String refreshToken = cookieRefreshToken;
        if (refreshToken == null && request != null) {
            refreshToken = request.getRefreshToken();
        }
        
        if (refreshToken == null) {
            throw new UnauthorizedException("Refresh token is required");
        }

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
        cookieUtils.clearCookie(response, REFRESH_COOKIE_NAME, REFRESH_COOKIE_PATH);
        return ResponseEntity.ok("Logged out successfully");
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        cookieUtils.addCookie(
                response,
                REFRESH_COOKIE_NAME,
                refreshToken,
                Duration.ofDays(tokenService.getRefreshTokenExpDays()),
                REFRESH_COOKIE_PATH
        );
    }

}
