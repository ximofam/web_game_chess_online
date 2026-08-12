package com.ximofam.graduation_project.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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

    public static long parseLong(Object val, long fallback) {
        if (val == null) return fallback;
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return fallback;
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

    public static UUID parseUuid(Object val) {
        return val == null ? null : UUID.fromString(val.toString());
    }

    public static Instant parseEpochMillis(Object val) {
        if (val == null) return null;
        try { return Instant.ofEpochMilli(Long.parseLong(val.toString())); }
        catch (NumberFormatException e) { return null; }
    }

    public static int toInt(Object val, int def) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) { try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {} }
        return def;
    }

    public static boolean toBool(Object val) {
        return Boolean.TRUE.equals(val) || "true".equals(val);
    }

    public static String orDefault(Object val, String def) {
        return val != null ? val.toString() : def;
    }
}
