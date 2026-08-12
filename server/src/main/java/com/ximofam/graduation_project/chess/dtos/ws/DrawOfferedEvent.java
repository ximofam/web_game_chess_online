package com.ximofam.graduation_project.chess.dtos.ws;

public record DrawOfferedEvent(String offeredBy) {
    public static final String TYPE = "DRAW_OFFERED";
}
