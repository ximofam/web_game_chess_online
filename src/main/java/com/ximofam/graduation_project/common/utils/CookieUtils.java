package com.ximofam.graduation_project.common.utils;

import com.ximofam.graduation_project.configs.properties.CookieProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class CookieUtils {

    private final CookieProperties cookieProperties;

    public void addCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
        addCookie(response, name, value, maxAge, "/");
    }

    public void addCookie(HttpServletResponse response, String name, String value,
                          Duration maxAge, String path) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .path(path)
                .maxAge(maxAge)
                .sameSite(cookieProperties.getSameSite())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearCookie(HttpServletResponse response, String name) {
        clearCookie(response, name, "/");
    }

    public void clearCookie(HttpServletResponse response, String name, String path) {
        addCookie(response, name, "", Duration.ZERO, path);
    }
}