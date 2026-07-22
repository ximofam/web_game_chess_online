package com.ximofam.graduation_project.users.services;

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
    private final RedisScript<Long> disconnectScript = RedisScript.of(new ClassPathResource("scripts/presence_disconnect.lua"), Long.class);
    private static final String ONLINE_USERS_KEY = "presence:online_users";
    private static final String ONLINE_COUNT_TOPIC = "/topic/presence.online-count";

    public void handleConnect(String userId, String sessionId) {
        Long becameOnline = redisTemplate.execute(
                connectScript,
                List.of(sessionsKey(userId), userKey(userId), ONLINE_USERS_KEY),
                sessionId, userId
        );

        redisTemplate.opsForValue().set(sessionKey(userId, sessionId), "1", sessionTtl);

        if (becameOnline != null && becameOnline == 1) {
            log.debug("User {} came online (session {})", userId, sessionId);
            broadcastOnlineUserCount();
        }
    }


    @Transactional
    public void handleDisconnect(String userId, String sessionId) {
        applyDisconnect(userId, sessionId);
        redisTemplate.delete(sessionKey(userId, sessionId));
    }


    @Transactional
    public void handleExpiredSession(String userId, String sessionId) {
        applyDisconnect(userId, sessionId);
    }

    private void applyDisconnect(String userId, String sessionId) {
        Long becameOffline = redisTemplate.execute(
                disconnectScript,
                List.of(sessionsKey(userId), userKey(userId), ONLINE_USERS_KEY),
                sessionId, userId
        );

        if (becameOffline != null && becameOffline == 1) {
            log.debug("User {} went offline, saving lastSeen to DB", userId);
            try {
                userRepository.updateLastSeen(Long.parseLong(userId), Instant.now());
            } catch (NumberFormatException e) {
                log.warn("Invalid userId format for lastSeen update: {}", userId);
            }
            broadcastOnlineUserCount();
        }
    }

    public void broadcastOnlineUserCount() {
        messagingTemplate.convertAndSend(ONLINE_COUNT_TOPIC, getOnlineUserCount());
    }


    public void handleHeartbeat(String userId, String sessionId) {
        redisTemplate.expire(sessionKey(userId, sessionId), sessionTtl);
    }

    public boolean isOnline(String userId) {
        return "online".equals(redisTemplate.opsForHash().get(userKey(userId), "status"));
    }

    public long getOnlineUserCount() {
        Long count = redisTemplate.opsForSet().size(ONLINE_USERS_KEY);
        return count != null ? count : 0L;
    }

    private String sessionsKey(String userId) {
        return "presence:sessions:" + userId;
    }

    private String userKey(String userId) {
        return "presence:user:" + userId;
    }

    private String sessionKey(String userId, String sessionId) {
        return "presence:session:" + userId + ":" + sessionId;
    }
}