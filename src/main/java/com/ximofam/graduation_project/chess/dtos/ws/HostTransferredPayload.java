package com.ximofam.graduation_project.chess.dtos.ws;

import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;

public record HostTransferredPayload(
        String newHostId,
        UserSimpleResponse newHost
) {
    public static final String TYPE = "HOST_TRANSFERRED";
}
