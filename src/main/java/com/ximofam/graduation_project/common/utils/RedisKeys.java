package com.ximofam.graduation_project.common.utils;

public final class RedisKeys {
    private RedisKeys() {
    }

    // Presence
    public static final String ONLINE_USERS = "sys:online_users";

    public static String presenceUser(String userId) {
        return "user:" + userId + ":presence";
    }

    public static String presenceSessions(String userId) {
        return "user:" + userId + ":sessions";
    }

    public static String presenceSessionDetail(String userId, String sessionId) {
        return "user:" + userId + ":session:" + sessionId;
    }

    // Room
    public static final String LOBBY_INDEX = "room:idx:lobby";

    public static String roomInfo(String roomId) {
        return "room:" + roomId;
    }
}
