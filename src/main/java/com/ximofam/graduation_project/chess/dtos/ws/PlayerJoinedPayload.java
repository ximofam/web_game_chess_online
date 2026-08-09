package com.ximofam.graduation_project.chess.dtos.ws;

import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;

public record PlayerJoinedPayload(
        String role,
        UserSimpleResponse user
) {
    public static String TYPE = "PLAYER_JOINED";
}
