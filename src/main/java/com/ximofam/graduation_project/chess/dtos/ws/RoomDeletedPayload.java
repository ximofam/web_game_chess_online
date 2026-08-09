package com.ximofam.graduation_project.chess.dtos.ws;

public record RoomDeletedPayload(String roomId) {
    public static String TYPE = "ROOM_DELETED";
}