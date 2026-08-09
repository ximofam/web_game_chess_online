package com.ximofam.graduation_project.chess.dtos.ws;

public record PlayerLeftPayload(
        String role,
        String userId
) {
    public static String TYPE = "PLAYER_LEFT";
}