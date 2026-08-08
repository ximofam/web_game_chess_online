package com.ximofam.graduation_project.chess.dtos.ws;

public record GameOverPayload(
        String winner,  // "white" | "black" | "draw"
        String reason   // "timeout" | "checkmate" | "stalemate" | "resign"
) {
    public static final String TYPE = "GAME_OVER";
}
