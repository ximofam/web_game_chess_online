package com.ximofam.graduation_project.common.utils;

import java.util.UUID;

import com.ximofam.graduation_project.auth.securities.CustomUserDetails;
import org.springframework.security.core.Authentication;

import java.security.Principal;

public class AuthUtils {

    private AuthUtils() {
        // Utility class
    }

    public static String resolveUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        if (principal instanceof Authentication auth) {
            Object p = auth.getPrincipal();
            if (p instanceof UUID id) {
                return id.toString();
            }
            if (p instanceof CustomUserDetails userDetails && userDetails.getUserId() != null) {
                return userDetails.getUserId().toString();
            }
        }
        return principal.getName();
    }
}
