package com.ximofam.graduation_project.common.ws;

import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;

public record PlayerJoinedPayload(String role, UserSimpleResponse user) {
}
