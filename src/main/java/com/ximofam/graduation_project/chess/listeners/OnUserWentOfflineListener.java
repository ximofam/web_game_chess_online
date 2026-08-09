package com.ximofam.graduation_project.chess.listeners;

import com.ximofam.graduation_project.chess.enums.LeaveReason;
import com.ximofam.graduation_project.chess.services.RoomService;
import com.ximofam.graduation_project.common.exceptions.http.BaseHttpException;
import com.ximofam.graduation_project.common.utils.RedisKeys;
import com.ximofam.graduation_project.users.dtos.events.UserWentOfflineEvent;
import com.ximofam.graduation_project.users.enums.PresenceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class OnUserWentOfflineListener {

    private final RoomService roomService;
    private final TaskScheduler scheduler;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.presence.offline-grace-period-seconds:5}")
    private long offlineGracePeriodSeconds = 5;

    @EventListener
    public void onUserWentOffline(UserWentOfflineEvent event) {
        String status = event.presenceData().getOrDefault("status", "");

        String roomId = event.presenceData().get("roomId");
        if (roomId == null) return;

        if (PresenceStatus.IN_ROOM.name().equalsIgnoreCase(status)) {
            scheduler.schedule(() -> {
                // If the user has not reconnected after the grace period, remove them
                if (!Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(RedisKeys.ONLINE_USERS, event.userId()))) {
                    try {
                        roomService.leaveRoom(roomId, event.userId(), LeaveReason.DISCONNECT);
                    } catch (BaseHttpException e) {
                        log.warn("Could not clean up room {} for disconnected user {}: {}", roomId, event.userId(), e.getMessage());
                    }
                }
            }, Instant.now().plusSeconds(offlineGracePeriodSeconds));
        }
    }
}
