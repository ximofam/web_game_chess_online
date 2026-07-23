package com.ximofam.graduation_project.chess.models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoomSettings {
    // Tiêu chuẩn Chess
    private int timeMinutes = 10;
    private int incrementSeconds = 0;
    private String variant = "STANDARD"; // STANDARD, CHESS960
    private boolean rated = false;

    // Cài đặt phòng
    private boolean isPrivate = false;
    private boolean chatLocked = false;
    private boolean spectatorLocked = false;
}
