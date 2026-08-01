package com.ximofam.graduation_project.chess.dtos.ws;

public record GameCountDownEvent(
        long startAt,
        long delayMillis
) {
    public static final String TYPE = "COUNTDOWN_STARTED";
}
