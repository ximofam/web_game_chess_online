package com.ximofam.graduation_project.chess.models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoomSettings {
    private boolean isPrivate;
    private boolean chatLocked;
    private boolean spectatorLocked;
    private int maxSpectators;
}
