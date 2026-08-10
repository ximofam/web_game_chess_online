package com.ximofam.graduation_project.chess.dtos.ws;

public record GameStartedEvent(
        String whiteId,
        String blackId,
        String turn,
        String fen,
        long whiteRemainingMillis,
        long blackRemainingMillis,
        long turnStartedAt
) {
    public static final String TYPE = "GAME_STARTED";
}
