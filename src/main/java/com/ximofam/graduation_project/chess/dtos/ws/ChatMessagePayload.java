package com.ximofam.graduation_project.chess.dtos.ws;

import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;

public record ChatMessagePayload(
        UserSimpleResponse sender,
        String message,
        long sentAt
) {
}