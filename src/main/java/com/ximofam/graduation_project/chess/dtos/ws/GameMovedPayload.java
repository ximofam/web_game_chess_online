package com.ximofam.graduation_project.chess.dtos.ws;

public record GameMovedPayload(
        String move,
        String color,
        String newTurn,
        String newFen,
        long whiteRemainingMillis,
        long blackRemainingMillis
) {
    public static final String TYPE = "MOVE_MADE";
}
