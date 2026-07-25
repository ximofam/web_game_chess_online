package com.ximofam.graduation_project.users.services;

import com.ximofam.graduation_project.chess.enums.RoomStatus;
import com.ximofam.graduation_project.common.utils.RedisKeys;
import com.ximofam.graduation_project.users.enums.PresenceStatus;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @DurationUnit(ChronoUnit.SECONDS)
    @Value("${app.presence.session-ttl-seconds:30}")
    private Duration sessionTtl = Duration.ofSeconds(30);

    private final RedisScript<Long> connectScript = RedisScript.of(new ClassPathResource("scripts/presence_connect.lua"), Long.class);
    private final RedisScript<List> disconnectScript = RedisScript.of(new ClassPathResource("scripts/presence_disconnect.lua"), List.class);

    public void handleConnect(String userId, String sessionId) {
        Long becameOnline = redisTemplate.execute(
                connectScript,
                List.of(RedisKeys.presenceSessions(userId),
                        RedisKeys.presenceUser(userId),
                        RedisKeys.ONLINE_USERS),
                sessionId, userId
        );

        redisTemplate.opsForValue().set(RedisKeys.presenceSessionDetail(userId, sessionId), "1", sessionTtl);

        if (becameOnline != null && becameOnline == 1) {
            log.debug("User {} came online (session {})", userId, sessionId);
            broadcastOnlineUserCount();
        }
    }


    @Transactional
    public void handleDisconnect(String userId, String sessionId) {
        applyDisconnect(userId, sessionId);
        redisTemplate.delete(RedisKeys.presenceSessionDetail(userId, sessionId));
    }


    @Transactional
    public void handleExpiredSession(String userId, String sessionId) {
        applyDisconnect(userId, sessionId);
    }

    private void applyDisconnect(String userId, String sessionId) {
        @SuppressWarnings("unchecked")
        List<Object> result = redisTemplate.execute(
                disconnectScript,
                List.of(RedisKeys.presenceSessions(userId),
                        RedisKeys.presenceUser(userId),
                        RedisKeys.ONLINE_USERS),
                sessionId, userId
        );

        if (result != null && !result.isEmpty()) {
            Long becameOffline = (Long) result.get(0);
            if (becameOffline == 1L) {
                log.debug("User {} went offline, saving lastSeen to DB", userId);
                try {
                    userRepository.updateLastSeen(Long.parseLong(userId), Instant.now());
                } catch (NumberFormatException e) {
                    log.warn("Invalid userId format for lastSeen update: {}", userId);
                }

                // Parse userData from Lua HGETALL (alternating keys and values)
                @SuppressWarnings("unchecked")
                List<String> userDataList = result.size() > 1 ? (List<String>) result.get(1) : List.of();
                Map<String, String> userData = new java.util.HashMap<>();
                for (int i = 0; i < userDataList.size() - 1; i += 2) {
                    userData.put(userDataList.get(i), userDataList.get(i + 1));
                }

                String status = userData.getOrDefault("status", PresenceStatus.ONLINE.name());

                if (PresenceStatus.IN_ROOM.name().equals(status)) {
                    String roomId = userData.get("roomId");
                    String isHost = userData.get("is_host");

                    if ("true".equals(isHost) && roomId != null) {
                        String roomKey = RedisKeys.roomInfo(roomId);
                        String roomStatus = (String) redisTemplate.opsForHash().get(roomKey, "status");

                        if (RoomStatus.WAITING.name().equals(roomStatus)) {
                            // Lấy white/black trước khi xóa phòng để reset presence của họ
                            List<Object> seats = redisTemplate.opsForHash().multiGet(roomKey, List.of("white", "black"));

                            redisTemplate.delete(roomKey);
                            redisTemplate.opsForZSet().remove(RedisKeys.LOBBY_INDEX, roomId);
                            log.debug("Cleaned up waiting room {} for offline host {}", roomId, userId);

                            // Reset presence của các player đang ngồi trong phòng
                            if (seats != null) {
                                for (Object seat : seats) {
                                    if (seat != null && !seat.toString().isBlank() && !seat.toString().equals(userId)) {
                                        String seatKey = RedisKeys.presenceUser(seat.toString());
                                        redisTemplate.opsForHash().delete(seatKey, "roomId", "is_host", "role");
                                        redisTemplate.opsForHash().put(seatKey, "status", PresenceStatus.ONLINE.name());
                                    }
                                }
                            }

                            // Broadcast tới lobby và trong phòng
                            Map<String, Object> deletedPayload = Map.of("type", "ROOM_DELETED", "data", Map.of("roomId", roomId));
                            messagingTemplate.convertAndSend("/topic/lobbies", (Object) deletedPayload);
                            messagingTemplate.convertAndSend("/topic/room/" + roomId, (Object) deletedPayload);
                        }
                    } else {
                        // Non-host disconnect: clear ghế của họ
                        String roomId2 = userData.get("roomId");
                        String role = userData.get("role");
                        if (roomId2 != null && role != null) {
                            redisTemplate.opsForHash().put(RedisKeys.roomInfo(roomId2), role, "");
                            messagingTemplate.convertAndSend("/topic/room/" + roomId2,
                                    (Object) Map.of("type", "PLAYER_LEFT", "data", Map.of("role", role, "userId", userId)));
                            messagingTemplate.convertAndSend("/topic/lobbies",
                                    (Object) Map.of("type", "ROOM_UPDATED", "data", Map.of("roomId", roomId2, "role", role, "user", Map.of())));
                        }
                    }

                    // Done with in_room, delete presence
                    redisTemplate.delete(RedisKeys.presenceUser(userId));
                } else if (PresenceStatus.IN_GAME.name().equals(status)) {
                    // ponytail: Keep presence hash alive for reconnection/timeout logic
                    log.debug("User {} disconnected while in_game. Keeping presence hash for reconnect.", userId);
                } else {
                    redisTemplate.delete(RedisKeys.presenceUser(userId));
                }

                broadcastOnlineUserCount();
            }
        }
    }

    public void broadcastOnlineUserCount() {
        messagingTemplate.convertAndSend("/topic/presence.online-count", getOnlineUserCount());
    }


    public void handleHeartbeat(String userId, String sessionId) {
        redisTemplate.expire(RedisKeys.presenceSessionDetail(userId, sessionId), sessionTtl);
    }

    public boolean isOnline(String userId) {
        String status = (String) redisTemplate.opsForHash().get(RedisKeys.presenceUser(userId), "status");
        return PresenceStatus.ONLINE.name().equals(status) || PresenceStatus.IN_ROOM.name().equals(status);
    }

    public boolean isInRoom(String userId) {
        return PresenceStatus.IN_ROOM.name().equals(redisTemplate.opsForHash().get(RedisKeys.presenceUser(userId), "status"));
    }

    public long getOnlineUserCount() {
        Long count = redisTemplate.opsForSet().size(RedisKeys.ONLINE_USERS);
        return count != null ? count : 0L;
    }

    public Map<String, Object> getUserPresence(String userId) {
        return redisTemplate.<String, Object>opsForHash().entries(RedisKeys.presenceUser(userId));
    }
}