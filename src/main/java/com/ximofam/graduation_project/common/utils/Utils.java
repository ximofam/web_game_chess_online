package com.ximofam.graduation_project.common.utils;

import java.security.SecureRandom;

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

}
