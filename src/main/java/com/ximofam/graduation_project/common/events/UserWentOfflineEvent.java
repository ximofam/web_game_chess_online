package com.ximofam.graduation_project.common.events;

import java.util.Map;

public record UserWentOfflineEvent(String userId, Map<String, String> presenceData) {}
