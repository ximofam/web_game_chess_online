package com.ximofam.graduation_project.common.helpers.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class JwtService {
    @Value("${app.security.jwt.access-token-exp-sec}")
    private long accessTokenExpSec;
    @Value("${app.security.jwt.secret-key}")
    private String secretKey;

    public String generateAccessToken(Object subject, List<String> roles) {
        return Jwts.builder()
                .subject(subject.toString())
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpSec * 1000L))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims verifyAndParseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
       
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> extractList(Claims claims, String key) {
        return Optional.ofNullable(claims.get(key, List.class))
                .orElse(List.of())
                .stream()
                .map(item -> (T) item.toString())
                .toList();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }
}
