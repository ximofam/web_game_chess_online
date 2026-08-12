package com.ximofam.graduation_project.chess.dtos.ws;

public record PlayerReadyEvent(
        String role,
        boolean isReady
) {
    public static final String TYPE = "PLAYER_READY";
}
