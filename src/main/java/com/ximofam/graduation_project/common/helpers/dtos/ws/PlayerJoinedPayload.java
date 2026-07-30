package com.ximofam.graduation_project.common.helpers.dtos.ws;

import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;

public record PlayerJoinedPayload(String role, UserSimpleResponse user) {
}
