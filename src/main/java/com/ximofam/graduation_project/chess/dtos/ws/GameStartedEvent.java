package com.ximofam.graduation_project.chess.dtos.ws;

public record GameStartedEvent(
        String whiteId,
        String blackId,
        String turn,
        String fen
) {
    public static final String TYPE = "GAME_STARTED";
}
