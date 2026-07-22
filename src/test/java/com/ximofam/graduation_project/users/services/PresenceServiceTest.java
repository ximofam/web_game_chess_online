package com.ximofam.graduation_project.users.services;

import com.ximofam.graduation_project.users.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    void handleConnect_ShouldExecuteConnectScriptSetSessionTTLAndBroadcastCount() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
        when(setOperations.size("presence:online_users")).thenReturn(1L);

        presenceService.handleConnect("1", "session1");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("presence:sessions:1", "presence:user:1", "presence:online_users")),
                eq("session1"),
                eq("1")
        );
        verify(valueOperations).set("presence:session:1:session1", "1", Duration.ofSeconds(30));
        verify(messagingTemplate).convertAndSend("/topic/presence.online-count", 1L);
    }

    @Test
    void handleDisconnect_ShouldExecuteDisconnectScriptSaveLastSeenDBDeleteSessionKeyAndBroadcastCount() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
        when(setOperations.size("presence:online_users")).thenReturn(0L);

        presenceService.handleDisconnect("1", "session1");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("presence:sessions:1", "presence:user:1", "presence:online_users")),
                eq("session1"),
                eq("1")
        );
        verify(userRepository).updateLastSeen(eq(1L), any(Instant.class));
        verify(redisTemplate).delete("presence:session:1:session1");
        verify(messagingTemplate).convertAndSend("/topic/presence.online-count", 0L);
    }

    @Test
    void handleExpiredSession_ShouldExecuteDisconnectScriptSaveLastSeenDBAndBroadcastCount() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
        when(setOperations.size("presence:online_users")).thenReturn(0L);

        presenceService.handleExpiredSession("1", "session1");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("presence:sessions:1", "presence:user:1", "presence:online_users")),
                eq("session1"),
                eq("1")
        );
        verify(userRepository).updateLastSeen(eq(1L), any(Instant.class));
        verify(redisTemplate, never()).delete(anyString());
        verify(messagingTemplate).convertAndSend("/topic/presence.online-count", 0L);
    }

    @Test
    void handleHeartbeat_ShouldExtendSessionTTL() {
        presenceService.handleHeartbeat("1", "session1");

        verify(redisTemplate).expire("presence:session:1:session1", Duration.ofSeconds(30));
    }

    @Test
    void isOnline_ShouldReturnTrueWhenStatusIsOnline() {
        when(hashOperations.get("presence:user:1", "status")).thenReturn("online");

        assertThat(presenceService.isOnline("1")).isTrue();
    }

    @Test
    void isOnline_ShouldReturnFalseWhenStatusIsNotOnline() {
        when(hashOperations.get("presence:user:1", "status")).thenReturn(null);

        assertThat(presenceService.isOnline("1")).isFalse();
    }

    @Test
    void getOnlineUserCount_ShouldReturnSizeFromRedisSet() {
        when(setOperations.size("presence:online_users")).thenReturn(5L);

        assertThat(presenceService.getOnlineUserCount()).isEqualTo(5L);
    }
}
