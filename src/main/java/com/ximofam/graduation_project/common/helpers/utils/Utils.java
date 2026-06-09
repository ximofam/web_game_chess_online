package com.ximofam.graduation_project.common.helpers.utils;

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
}
