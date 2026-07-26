package com.ximofam.graduation_project.users.services;

import com.ximofam.graduation_project.common.events.UserWentOfflineEvent;
import com.ximofam.graduation_project.common.utils.RedisKeys;
import com.ximofam.graduation_project.users.enums.PresenceStatus;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
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


@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

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


    public void handleDisconnect(String userId, String sessionId) {
        applyDisconnect(userId, sessionId);
        redisTemplate.delete(RedisKeys.presenceSessionDetail(userId, sessionId));
    }

    public void handleExpiredSession(String userId, String sessionId) {
        applyDisconnect(userId, sessionId);
    }

    @SuppressWarnings("unchecked")
    private void applyDisconnect(String userId, String sessionId) {
        List<Object> result = redisTemplate.execute(
                disconnectScript,
                List.of(RedisKeys.presenceSessions(userId),
                        RedisKeys.presenceUser(userId),
                        RedisKeys.ONLINE_USERS),
                sessionId, userId
        );

        if (result == null || result.isEmpty() || !Long.valueOf(1L).equals(result.get(0))) return;

        log.debug("User {} went offline, saving lastSeen to DB", userId);
        try {
            userRepository.updateLastSeen(Long.parseLong(userId), Instant.now());
        } catch (NumberFormatException e) {
            log.warn("Invalid userId format for lastSeen update: {}", userId);
        }

        Map<String, String> presenceData = parseHgetall(
                result.size() > 1 ? (List<String>) result.get(1) : List.of()
        );

        eventPublisher.publishEvent(new UserWentOfflineEvent(userId, presenceData));

        // ponytail: keep presence hash alive for IN_GAME reconnect; upgrade to TTL-based expiry when needed
        if (!PresenceStatus.IN_GAME.name().equals(presenceData.getOrDefault("status", ""))) {
            redisTemplate.delete(RedisKeys.presenceUser(userId));
        } else {
            log.debug("User {} disconnected while in_game. Keeping presence hash for reconnect.", userId);
        }

        broadcastOnlineUserCount();
    }

    private static Map<String, String> parseHgetall(List<String> flat) {
        Map<String, String> map = new HashMap<>(flat.size());
        for (int i = 0; i + 1 < flat.size(); i += 2) map.put(flat.get(i), flat.get(i + 1));
        return map;
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