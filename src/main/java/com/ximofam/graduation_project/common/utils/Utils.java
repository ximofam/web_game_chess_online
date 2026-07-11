package com.ximofam.graduation_project.common.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.security.SecureRandom;
import java.time.Duration;

public class Utils {
    public static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    public static String getRole(Enum<?> roleEnum) {
        if (roleEnum == null) {
            return null;
        }
        return getRole(roleEnum.name());
    }

    public static String getRole(String role) {
        return "ROLE_" + role;
    }

    private static final String CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomIndex = RANDOM.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(randomIndex));
        }
        return sb.toString();
    }

    public static String extractValueFromCookie(HttpServletRequest request, String cookieName) {
        if (request == null || cookieName == null || cookieName.isEmpty()) {
            return null;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    public static void addCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
        addCookie(response, name, value, maxAge, "/", "Lax", false);
    }

    public static void addCookie(HttpServletResponse response, String name, String value,
                                 Duration maxAge, String path, String sameSite, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .maxAge(maxAge)
                .sameSite(sameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public static void clearCookie(HttpServletResponse response, String name) {
        clearCookie(response, name, "/", "Lax", false);
    }

    public static void clearCookie(HttpServletResponse response, String name,
                                   String path, String sameSite, boolean secure) {
        addCookie(response, name, "", Duration.ZERO, path, sameSite, secure);
    }
}
