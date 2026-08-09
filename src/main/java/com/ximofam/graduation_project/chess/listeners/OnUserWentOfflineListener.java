package com.ximofam.graduation_project.chess.listeners;

import com.ximofam.graduation_project.chess.enums.LeaveReason;
import com.ximofam.graduation_project.chess.services.RoomService;
import com.ximofam.graduation_project.common.exceptions.http.BaseHttpException;
import com.ximofam.graduation_project.users.dtos.events.UserWentOfflineEvent;
import com.ximofam.graduation_project.users.enums.PresenceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OnUserWentOfflineListener {

    private final RoomService roomService;

    @EventListener
    public void onUserWentOffline(UserWentOfflineEvent event) {
        String status = event.presenceData().getOrDefault("status", "");

        String roomId = event.presenceData().get("roomId");
        if (roomId == null) return;

        if (PresenceStatus.IN_ROOM.name().equalsIgnoreCase(status)) {
            try {
                roomService.leaveRoom(roomId, event.userId(), LeaveReason.DISCONNECT);
            } catch (BaseHttpException e) {
                log.warn("Could not clean up room {} for disconnected user {}: {}", roomId, event.userId(), e.getMessage());
            }
        }
    }
}
