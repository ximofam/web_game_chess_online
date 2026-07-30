package com.ximofam.graduation_project.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;

public class Utils {
    private Utils() {
        /* This utility class should not be instantiated */
    }

    public static String CREATED_AT_FIELD = "createdAt";

    public static boolean hasText(String text) {
        return text != null && !text.isBlank();
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

    public static String str(Map<Object, Object> raw, String key) {
        Object v = raw.get(key);
        return (v == null || v.toString().isBlank()) ? null : v.toString();
    }

    public static long parseLong(Map<Object, Object> raw, String key) {
        try {
            return Long.parseLong(Objects.requireNonNull(str(raw, key)));
        } catch (Exception e) {
            return 0L;
        }
    }

    public static <T> T parseJson(ObjectMapper mapper, String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static <T> T parseJson(ObjectMapper mapper, Object json, Class<T> type) {
        return parseJson(mapper, json.toString(), type);
    }

    public static String writeJson(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
