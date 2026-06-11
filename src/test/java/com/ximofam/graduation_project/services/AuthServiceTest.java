package com.ximofam.graduation_project.services;

import com.ximofam.graduation_project.common.helpers.services.JwtService;
import com.ximofam.graduation_project.common.securities.CustomUserDetails;
import com.ximofam.graduation_project.users.dtos.request.LoginRequest;
import com.ximofam.graduation_project.users.dtos.response.TokenResponse;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.entities.enums.UserRole;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import com.ximofam.graduation_project.users.services.AuthService;
import com.ximofam.graduation_project.users.services.RefreshTokenService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    AuthenticationManager authenticationManager;
    @Mock
    UserRepository userRepository;
    @Mock
    JwtService jwtService;
    @Mock
    RefreshTokenService refreshTokenService;
    @InjectMocks
    AuthService authService;


    private LoginRequest loginRequest;
    private CustomUserDetails mockUserDetails;
    private User mockUser;

    @BeforeEach
    void setUp() {
        loginRequest = new LoginRequest();
        loginRequest.setUsernameOrEmail("test@gmail.com");
        loginRequest.setPassword("password123");

        mockUserDetails = CustomUserDetails.builder("test@gmail.com", "hashed")
                .userId(1L)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("test@gmail.com");
        mockUser.setRole(UserRole.USER);
    }

    @Test
    void login_WithValidCredentials_ReturnTokenResponse() {
        Authentication mockAuth = mock(Authentication.class);
        when(mockAuth.getPrincipal()).thenReturn(mockUserDetails);
        when(authenticationManager.authenticate(any())).thenReturn(mockAuth);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        when(jwtService.generateAccessToken(any(), any())).thenReturn("mock-access-token");
        when(refreshTokenService.generateRefreshToken(any())).thenReturn("mock-refresh-token");

        TokenResponse result = authService.login(loginRequest);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getAccessToken()).isEqualTo("mock-access-token");
        Assertions.assertThat(result.getRefreshToken()).isEqualTo("mock-refresh-token");
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    void login_WithWrongCredentials_ThrowBadCredentialsException() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    void login_WhenUserIdNotFoundInDB_ThrowNotFoundException() {
        Authentication mockAuth = mock(Authentication.class);
        when(mockAuth.getPrincipal()).thenReturn(mockUserDetails);
        when(authenticationManager.authenticate(any())).thenReturn(mockAuth);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(com.ximofam.graduation_project.common.exceptions.http.NotFoundException.class)
                .hasMessageContaining("1");
    }

    @Test
    void login_WithLockedAccount_ThrowLockedException() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new LockedException("Account locked"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(LockedException.class);
    }
}