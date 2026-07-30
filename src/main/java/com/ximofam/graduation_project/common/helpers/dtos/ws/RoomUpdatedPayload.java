package com.ximofam.graduation_project.common.helpers.dtos.ws;

import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;

public record RoomUpdatedPayload(String roomId, String role, UserSimpleResponse user) {
}