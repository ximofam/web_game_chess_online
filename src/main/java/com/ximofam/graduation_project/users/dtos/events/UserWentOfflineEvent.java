package com.ximofam.graduation_project.users.dtos.events;

import java.util.Map;

public record UserWentOfflineEvent(String userId, Map<String, String> presenceData) {
}
