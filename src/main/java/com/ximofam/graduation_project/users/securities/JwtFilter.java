package com.ximofam.graduation_project.users.securities;

import com.ximofam.graduation_project.common.helpers.services.JwtService;
import com.ximofam.graduation_project.users.entities.enums.UserRole;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(7);
            Claims claims = jwtService.verifyAndParseToken(token);
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                Long userId = jwtService.extractUserId(claims);
                String role = jwtService.extractRole(claims);
                UserRole userRole = UserRole.valueOf(role);

                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, userRole.getAuthorities())
                );
            }
        } catch (Exception ignore) {

        }

        filterChain.doFilter(request, response);
    }
}
