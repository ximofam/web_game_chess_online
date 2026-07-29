package com.ximofam.graduation_project.auth.services;

import com.ximofam.graduation_project.auth.RefreshSession;
import com.ximofam.graduation_project.auth.dtos.response.TokenResponse;
import com.ximofam.graduation_project.auth.enums.TokenType;
import com.ximofam.graduation_project.common.exceptions.http.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenService {

    @Value("${app.security.jwt.access-token-exp-sec}")
    private long accessTokenExpSec;
    @Getter
    @Value("${app.security.jwt.refresh-token-exp-days}")
    private long refreshTokenExpDays;
    @Value("${app.security.jwt.secret-key}")
    private String secretKey;
    @Value("${app.user.guest-max-age-days}")
    private int guestMaxAgeDays;
    private SecretKey signingKey;
    private final RedisTemplate<String, Object> redisTemplate;

    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }

    public String generateAccessToken(Long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("type", TokenType.ACCESS.toValue())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpSec * 1000L))
                .signWith(signingKey)
                .compact();
    }


    public String generateRefreshToken(Long userId, String role) {
        String jti = UUID.randomUUID().toString();
        RefreshSession session = new RefreshSession();
        session.setUserId(userId);
        session.setUserRole(role);
        redisTemplate.opsForValue().set(
                buildRefreshTokenKey(jti),
                session,
                refreshTokenExpDays * 24 * 60 * 60, TimeUnit.SECONDS);

        return Jwts.builder()
                .id(jti)
                .claim("type", TokenType.REFRESH.toValue())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpDays * 24 * 60 * 60 * 1000L))
                .signWith(signingKey)
                .compact();
    }

    public String generateGuestToken(Long guestId) {
        long daysInMillis = guestMaxAgeDays * 24L * 60L * 60L * 1000L;

        return Jwts.builder()
                .subject(String.valueOf(guestId))
                .claim("type", TokenType.GUEST.toValue())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + daysInMillis))
                .signWith(signingKey)
                .compact();
    }

    public Claims verifyAndParseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Claims verifyAndParseToken(String token, TokenType type) {
        Claims claims = verifyAndParseToken(token);
        if (!type.toValue().equals(claims.get("type", String.class))) {
            throw new UnauthorizedException("Token type không hợp lệ.");
        }

        return claims;
    }

    public TokenResponse refresh(String refreshToken) {
        Claims claims = verifyAndParseToken(refreshToken, TokenType.REFRESH);

        String jti = extractJti(claims);
        RefreshSession session = (RefreshSession) redisTemplate.opsForValue().get(buildRefreshTokenKey(jti));
        if (session == null) {
            throw new UnauthorizedException("Refresh token is invalid or has expired.");
        }

        redisTemplate.delete(buildRefreshTokenKey(jti));

        return generateTokens(session.getUserId(), session.getUserRole());
    }

    public void deleteRefreshSession(String refreshToken) {
        Claims claims = verifyAndParseToken(refreshToken, TokenType.REFRESH);

        String jti = extractJti(claims);
        redisTemplate.delete(buildRefreshTokenKey(jti));
    }

    public TokenResponse generateTokens(Long userId, String userRole) {
        String accessToken = generateAccessToken(userId, userRole);
        String refreshToken = generateRefreshToken(userId, userRole);

        return new TokenResponse(accessToken, refreshToken);
    }

    public Long extractUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public String extractJti(Claims claims) {
        return claims.getId();
    }

    private String buildRefreshTokenKey(String jti) {
        return "refresh_token:" + jti;
    }
}