package com.ximofam.graduation_project.users.dtos.events;

public record UserPresenceChangedEvent(String userId, boolean isOffline) {
    public UserPresenceChangedEvent(String userId) {
        this(userId, false);
    }
}
