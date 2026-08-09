package com.ximofam.graduation_project.users.services;

import com.ximofam.graduation_project.common.helpers.dtos.WsEvent;
import com.ximofam.graduation_project.common.utils.RedisKeys;
import com.ximofam.graduation_project.common.utils.TopicUtils;
import com.ximofam.graduation_project.users.dtos.events.UserPresenceChangedEvent;
import com.ximofam.graduation_project.users.dtos.events.UserWentOfflineEvent;
import com.ximofam.graduation_project.users.dtos.ws.UserPresenceEvent;
import com.ximofam.graduation_project.users.enums.PresenceStatus;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final RedissonClient redissonClient;

    @DurationUnit(ChronoUnit.SECONDS)
    @Value("${app.presence.session-ttl-seconds:30}")
    private Duration sessionTtl = Duration.ofSeconds(30);

    private final RedisScript<Long> presenceConnectScript;
    private final RedisScript<Long> handlePlayingDisconnectScript;
    private final RedisScript<List<Object>> presenceDisconnectScript;
    private final RedisScript<Long> presenceSetStatusScript;

    public void handleConnect(String userId, String sessionId) {
        Long becameOnline = redisTemplate.execute(
                presenceConnectScript,
                List.of(RedisKeys.presenceSessions(userId),
                        RedisKeys.presenceUser(userId),
                        RedisKeys.ONLINE_USERS),
                sessionId, userId
        );

        redisTemplate.opsForValue().set(RedisKeys.presenceSessionDetail(userId, sessionId), "1", sessionTtl);

        if (Long.valueOf(1L).equals(becameOnline)) {
            log.debug("User {} came online (session {})", userId, sessionId);
            broadcastOnlineUserCount();
        }

        eventPublisher.publishEvent(new UserPresenceChangedEvent(userId));
    }


    public void handleDisconnect(String userId, String sessionId) {
        applyDisconnect(userId, sessionId);
        redisTemplate.delete(RedisKeys.presenceSessionDetail(userId, sessionId));
    }

    public void handleExpiredSession(String userId, String sessionId) {
        applyDisconnect(userId, sessionId);
    }

    @SuppressWarnings("unchecked")
    private void applyDisconnect(String userId, String sessionId) {
        RLock lock = redissonClient.getLock(RedisKeys.lockKey("presence:" + userId));
        try {
            if (!lock.tryLock(3, 5, TimeUnit.SECONDS)) {
                log.warn("Could not acquire presence lock for disconnect: {}", userId);
                return;
            }

            List<Object> result = redisTemplate.execute(
                    presenceDisconnectScript,
                    List.of(RedisKeys.presenceSessions(userId),
                            RedisKeys.presenceUser(userId),
                            RedisKeys.ONLINE_USERS),
                    sessionId, userId
            );

            if (result == null || result.isEmpty() || !Long.valueOf(1L).equals(result.getFirst())) return;

            updateLastSeenSafe(userId);

            Map<String, String> presenceData = parseHgetall(result.size() > 1 ? (List<String>) result.get(1) : List.of());

            messagingTemplate.convertAndSend(TopicUtils.user(userId),
                    WsEvent.of(UserPresenceEvent.TYPE, Map.of("status", PresenceStatus.OFFLINE.name())));

            eventPublisher.publishEvent(new UserPresenceChangedEvent(userId, true));
            eventPublisher.publishEvent(new UserWentOfflineEvent(userId, presenceData));

            String status = presenceData.getOrDefault("status", "").toUpperCase();
            if (PresenceStatus.PLAYING.name().equalsIgnoreCase(status)) {
                String roomId = presenceData.get("roomId");
                String role = presenceData.getOrDefault("role", "").toLowerCase();

                if (roomId != null && !role.isEmpty()) {
                    redisTemplate.execute(
                            handlePlayingDisconnectScript,
                            List.of(RedisKeys.presenceUser(userId), RedisKeys.gameInfo(roomId)),
                            role, String.valueOf(System.currentTimeMillis())
                    );
                    log.debug("User {} disconnected while in_game. Calculated precise EX for reconnect.", userId);
                } else {
                    redisTemplate.delete(RedisKeys.presenceUser(userId));
                }
            } else if (PresenceStatus.IN_ROOM.name().equalsIgnoreCase(status)) {
                redisTemplate.expire(RedisKeys.presenceUser(userId), Duration.ofSeconds(5));
            } else {
                redisTemplate.delete(RedisKeys.presenceUser(userId));
            }

            broadcastOnlineUserCount();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock interrupted on disconnect", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void broadcastOnlineUserCount() {
        messagingTemplate.convertAndSend("/topic/presence.online-count", getOnlineUserCount());
    }

    private void updateLastSeenSafe(String userId) {
        log.debug("User {} went offline, saving lastSeen to DB", userId);
        try {
            userRepository.updateLastSeen(UUID.fromString(userId), Instant.now());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format for lastSeen update: {}", userId);
        }
    }


    public void handleHeartbeat(String userId, String sessionId) {
        redisTemplate.expire(RedisKeys.presenceSessionDetail(userId, sessionId), sessionTtl);
    }

    public void setPresenceStatus(String userId, PresenceStatus status, String... delFields) {
        Object[] args = new Object[1 + (delFields != null ? delFields.length : 0)];
        args[0] = status.name();
        if (delFields != null && delFields.length > 0) {
            System.arraycopy(delFields, 0, args, 1, delFields.length);
        }

        Long success = redisTemplate.execute(
                presenceSetStatusScript,
                List.of(RedisKeys.presenceUser(userId), RedisKeys.presenceSessions(userId)),
                (Object[]) args
        );

        if (success != null && success == 1L) {
            eventPublisher.publishEvent(new UserPresenceChangedEvent(userId));
        }
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
        Map<String, Object> res = redisTemplate.<String, Object>opsForHash().entries(RedisKeys.presenceUser(userId));
        if (res == null || res.isEmpty()) {
            return Map.of("status", PresenceStatus.OFFLINE.name());
        }
        return res;
    }

    private static Map<String, String> parseHgetall(List<String> flat) {
        Map<String, String> map = HashMap.newHashMap(flat.size());
        for (int i = 0; i + 1 < flat.size(); i += 2) map.put(flat.get(i), flat.get(i + 1));
        return map;
    }
}