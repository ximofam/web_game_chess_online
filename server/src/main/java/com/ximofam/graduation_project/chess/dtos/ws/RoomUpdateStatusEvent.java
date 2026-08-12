package com.ximofam.graduation_project.chess.dtos.ws;

public record RoomUpdateStatusEvent(
        String roomId,
        String status
) {
    public static final String TYPE = "ROOM_UPDATED";
}
