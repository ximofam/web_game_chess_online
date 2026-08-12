package com.ximofam.graduation_project.chess.dtos.ws;

import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;

public record SeatSwitchedPayload(
        String fromRole,
        String toRole,
        UserSimpleResponse user
) {
    public static final String TYPE = "SEAT_SWITCHED";
}
