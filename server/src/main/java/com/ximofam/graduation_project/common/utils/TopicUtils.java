package com.ximofam.graduation_project.common.utils;

public class TopicUtils {
    private TopicUtils() {
        // Prevent instantiation
    }

    public static String room(String roomId) {
        return "/topic/room." + roomId;
    }

    public static String LOBBIES = "/topic/lobbies";

    public static String user(String userId) {
        return "/topic/user." + userId;
    }
}
