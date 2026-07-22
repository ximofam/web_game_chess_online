package com.ximofam.graduation_project.users.services;

import com.ximofam.graduation_project.users.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

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
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void handleConnect_ShouldExecuteConnectScriptAndSetSessionTTL() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        presenceService.handleConnect("1", "session1");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("presence:sessions:1", "presence:user:1")),
                eq("session1")
        );
        verify(valueOperations).set("presence:session:1:session1", "1", Duration.ofSeconds(30));
    }

    @Test
    void handleDisconnect_ShouldExecuteDisconnectScriptSaveLastSeenDBAndDeleteSessionKey() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        presenceService.handleDisconnect("1", "session1");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("presence:sessions:1", "presence:user:1")),
                eq("session1")
        );
        verify(userRepository).updateLastSeen(eq(1L), any(Instant.class));
        verify(redisTemplate).delete("presence:session:1:session1");
    }

    @Test
    void handleExpiredSession_ShouldExecuteDisconnectScriptAndSaveLastSeenDB() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        presenceService.handleExpiredSession("1", "session1");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("presence:sessions:1", "presence:user:1")),
                eq("session1")
        );
        verify(userRepository).updateLastSeen(eq(1L), any(Instant.class));
        verify(redisTemplate, never()).delete(anyString());
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
}
