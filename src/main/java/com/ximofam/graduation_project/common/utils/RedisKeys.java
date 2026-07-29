package com.ximofam.graduation_project.common.utils;

public final class RedisKeys {
    private RedisKeys() {
    }

    // Presence
    public static final String ONLINE_USERS = "sys:online_users";
    private static final String USER_PREFIX = "user:";

    public static String presenceUser(String userId) {
        return USER_PREFIX + userId + ":presence";
    }

    public static String presenceSessions(String userId) {
        return USER_PREFIX + userId + ":sessions";
    }

    public static String presenceSessionDetail(String userId, String sessionId) {
        return USER_PREFIX + userId + ":session:" + sessionId;
    }

    // Room
    private static final String ROOM_PREFIX = "room:";
    public static final String LOBBY_INDEX = "room:idx:lobby";

    public static String roomInfo(String roomId) {
        return ROOM_PREFIX + roomId;
    }

    public static String roomSpectators(String roomId) {
        return ROOM_PREFIX + roomId + ":spectators";
    }

    public static String roomChat(String roomId) {
        return ROOM_PREFIX + roomId + ":chat";
    }
}
