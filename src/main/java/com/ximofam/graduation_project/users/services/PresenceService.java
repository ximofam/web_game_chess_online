package com.ximofam.graduation_project.users.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final StringRedisTemplate redisTemplate;
    private static final Duration SESSION_TTL = Duration.ofSeconds(30);

    private final RedisScript<Long> connectScript =
            RedisScript.of(new ClassPathResource("scripts/presence_connect.lua"), Long.class);

    private final RedisScript<Long> disconnectScript =
            RedisScript.of(new ClassPathResource("scripts/presence_disconnect.lua"), Long.class);


    public void handleConnect(String userId, String sessionId) {
        String now = String.valueOf(Instant.now().getEpochSecond());

        Long becameOnline = redisTemplate.execute(
                connectScript,
                List.of(sessionsKey(userId), userKey(userId)),
                sessionId, now
        );

        redisTemplate.opsForValue().set(sessionKey(userId, sessionId), "1", SESSION_TTL);

        if (becameOnline != null && becameOnline == 1) {
            log.debug("User {} came online (session {})", userId, sessionId);
            // TODO: publish event qua RabbitMQ/Redis Pub-Sub nếu có nhiều instance
            // và cần broadcast trạng thái online cho toàn bộ cluster.
        }
    }


    public void handleDisconnect(String userId, String sessionId) {
        applyDisconnect(userId, sessionId);
        redisTemplate.delete(sessionKey(userId, sessionId));
    }


    public void handleExpiredSession(String userId, String sessionId) {
        applyDisconnect(userId, sessionId);
    }

    private void applyDisconnect(String userId, String sessionId) {
        String now = String.valueOf(Instant.now().getEpochSecond());

        Long becameOffline = redisTemplate.execute(
                disconnectScript,
                List.of(sessionsKey(userId), userKey(userId)),
                sessionId, now
        );

        if (becameOffline != null && becameOffline == 1) {
            log.debug("User {} went offline", userId);
            // TODO: publish event tương tự như handleConnect
        }
    }


    public void handleHeartbeat(String userId, String sessionId) {
        redisTemplate.expire(sessionKey(userId, sessionId), SESSION_TTL);
    }

    public boolean isOnline(String userId) {
        return "online".equals(redisTemplate.opsForHash().get(userKey(userId), "status"));
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