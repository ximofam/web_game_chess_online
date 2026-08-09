package com.ximofam.graduation_project.chess.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PlayerRole {
    BLACK,
    WHITE,
    HOST,
    SPECTATOR;

    public static PlayerRole load(String role) {
        for (PlayerRole playerRole : PlayerRole.values()) {
            if (playerRole.name().equalsIgnoreCase(role)) {
                return playerRole;
            }
        }
        throw new IllegalArgumentException("Invalid role: " + role);
    }

    public static PlayerRole nextTurn(PlayerRole blackOrWhite) {
        if (BLACK.equals(blackOrWhite)) {
            return WHITE;
        } else if (WHITE.equals(blackOrWhite)) {
            return BLACK;
        }
        throw new IllegalArgumentException("Invalid role: " + blackOrWhite);
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }

}
