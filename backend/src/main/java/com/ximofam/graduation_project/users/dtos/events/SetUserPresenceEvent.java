package com.ximofam.graduation_project.users.dtos.events;

import com.ximofam.graduation_project.users.enums.PresenceStatus;
import java.util.Map;

public record SetUserPresenceEvent(
        String userId,
        PresenceStatus status,
        Map<String, String> data
) {
}
