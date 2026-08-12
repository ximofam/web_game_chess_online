package com.ximofam.graduation_project.common.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(@NonNull HttpServletRequest request,
                       HttpServletResponse response,
                       @NonNull AccessDeniedException ex)
            throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");

        response.getWriter().write("""
                {
                  "code": "FORBIDDEN",
                  "message": "Access denied"
                }
                """);
    }
}