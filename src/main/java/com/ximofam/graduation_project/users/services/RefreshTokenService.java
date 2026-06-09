package com.ximofam.graduation_project.users.services;

import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.common.exceptions.http.UnauthorizedException;
import com.ximofam.graduation_project.users.entities.RefreshToken;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.repositories.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    @Value("${app.security.refresh-token.exp-days}")
    private long refreshTokenExpDays;

    public String generateRefreshToken(User user) {
        String rawToken = generateRefreshTokenStr();
        String tokenHash = hashRefreshTokenStr(rawToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.now().plus(refreshTokenExpDays, ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    public RefreshToken verifyAndGetRefreshToken(String rawToken) {
        String tokenHash = hashRefreshTokenStr(rawToken);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid token"));

        if (refreshToken.isRevoked()) {
            throw new UnauthorizedException("Token đã bị thu hồi");
        }

        if (refreshToken.isExpired()) {
            throw new UnauthorizedException("Token đã hết hạn");
        }

        return refreshToken;
    }

    @Transactional
    public void revokeRefreshToken(Long id) {
        int rowsAffect = refreshTokenRepository.revokeTokenById(id);
        if (rowsAffect == 0) {
            throw new NotFoundException("RefreshTokenId %d không tồn tại", id);
        }
    }

    private String generateRefreshTokenStr() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String hashRefreshTokenStr(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
