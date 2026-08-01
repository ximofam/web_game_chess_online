package com.ximofam.graduation_project.chess.dtos.ws;

public record GameStartPayload(
        String gameId,
        long startAt
) {
}
