package com.ximofam.graduation_project.chess.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PlayerRole {
    BLACK,
    WHITE,
    HOST,
    SPECTATOR;

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }
}
