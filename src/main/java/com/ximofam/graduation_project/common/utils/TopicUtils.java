package com.ximofam.graduation_project.common.utils;

public class TopicUtils {
    private TopicUtils() {
        // Prevent instantiation
    }

    public static String room(String roomId) {
        return "/topic/room." + roomId;
    }
}
