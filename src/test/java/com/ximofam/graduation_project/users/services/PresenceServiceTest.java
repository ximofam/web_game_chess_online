package com.ximofam.graduation_project.users.services;

import com.ximofam.graduation_project.users.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private SetOperations<String, String> setOperations;
    @Mock private RedisScript<Long> presenceConnectScript;
    @SuppressWarnings("rawtypes") @Mock private RedisScript presenceDisconnectScript;
    @Mock private RedisScript<Long> presenceSetStatusScript;

    private PresenceService presenceService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // Manual construction: avoids @InjectMocks generic confusion with two RedisScript fields
        presenceService = new PresenceService(redisTemplate, userRepository, messagingTemplate,
                eventPublisher, presenceConnectScript, presenceDisconnectScript, presenceSetStatusScript);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    // Redis key constants under test (derived from RedisKeys)
    private static final String SESSIONS_1    = "user:1:sessions";
    private static final String PRESENCE_1    = "user:1:presence";
    private static final String ONLINE_USERS  = "sys:online_users";
    private static final String SESSION_DETAIL = "user:1:session:session1";

    // ── handleConnect ────────────────────────────────────────────────────────────

    @Test
    void handleConnect_FirstSession_ShouldRunScriptSetTTLAndBroadcastCount() {
        when(redisTemplate.execute(eq(presenceConnectScript),
                eq(List.of(SESSIONS_1, PRESENCE_1, ONLINE_USERS)),
                eq("session1"), eq("1"))).thenReturn(1L);
        when(setOperations.size(ONLINE_USERS)).thenReturn(1L);

        presenceService.handleConnect("1", "session1");

        verify(valueOperations).set(SESSION_DETAIL, "1", Duration.ofSeconds(30));
        verify(messagingTemplate).convertAndSend("/topic/presence.online-count", 1L);
    }

    @Test
    void handleConnect_AlreadyOnline_ShouldNotBroadcastCount() {
        when(redisTemplate.execute(eq(presenceConnectScript),
                eq(List.of(SESSIONS_1, PRESENCE_1, ONLINE_USERS)),
                eq("session1"), eq("1"))).thenReturn(0L);  // 0 = already online

        presenceService.handleConnect("1", "session1");

        verify(valueOperations).set(SESSION_DETAIL, "1", Duration.ofSeconds(30));
        verifyNoInteractions(messagingTemplate);
    }

    // ── handleDisconnect ─────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void handleDisconnect_LastSession_ShouldSaveLastSeenDeleteKeysAndBroadcast() {
        // result[0]=1 → last session; result[1]=flat HGETALL (empty → no presenceData)
        when(redisTemplate.execute(eq(presenceDisconnectScript),
                eq(List.of(SESSIONS_1, PRESENCE_1, ONLINE_USERS)),
                eq("session1"), eq("1"))).thenReturn(List.of(1L, List.of()));
        when(setOperations.size(ONLINE_USERS)).thenReturn(0L);

        presenceService.handleDisconnect("1", "session1");

        verify(userRepository).updateLastSeen(eq(1L), any(Instant.class));
        // applyDisconnect deletes presence key (status != IN_GAME)
        verify(redisTemplate).delete(PRESENCE_1);
        // handleDisconnect deletes session detail key
        verify(redisTemplate).delete(SESSION_DETAIL);
        verify(messagingTemplate).convertAndSend("/topic/presence.online-count", 0L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleDisconnect_NotLastSession_ShouldDoNothingBeyondScriptAndSessionDelete() {
        // result[0]=0 → still has other sessions, applyDisconnect returns early
        when(redisTemplate.execute(eq(presenceDisconnectScript),
                eq(List.of(SESSIONS_1, PRESENCE_1, ONLINE_USERS)),
                eq("session1"), eq("1"))).thenReturn(List.of(0L));

        presenceService.handleDisconnect("1", "session1");

        verifyNoInteractions(userRepository);
        // only the session detail key is deleted (by handleDisconnect itself)
        verify(redisTemplate).delete(SESSION_DETAIL);
        verifyNoInteractions(messagingTemplate);
    }

    // ── handleExpiredSession ─────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void handleExpiredSession_LastSession_ShouldSaveLastSeenDeletePresenceKeyAndBroadcast() {
        when(redisTemplate.execute(eq(presenceDisconnectScript),
                eq(List.of(SESSIONS_1, PRESENCE_1, ONLINE_USERS)),
                eq("session1"), eq("1"))).thenReturn(List.of(1L, List.of()));
        when(setOperations.size(ONLINE_USERS)).thenReturn(0L);

        presenceService.handleExpiredSession("1", "session1");

        verify(userRepository).updateLastSeen(eq(1L), any(Instant.class));
        // applyDisconnect deletes presence key
        verify(redisTemplate).delete(PRESENCE_1);
        // handleExpiredSession does NOT delete session detail (already expired by Redis TTL)
        verify(redisTemplate, never()).delete(SESSION_DETAIL);
        verify(messagingTemplate).convertAndSend("/topic/presence.online-count", 0L);
    }

    // ── handleHeartbeat ──────────────────────────────────────────────────────────

    @Test
    void handleHeartbeat_ShouldExtendSessionTTL() {
        presenceService.handleHeartbeat("1", "session1");

        verify(redisTemplate).expire(SESSION_DETAIL, Duration.ofSeconds(30));
    }

    // ── isOnline / isInRoom ───────────────────────────────────────────────────────

    @Test
    void isOnline_ShouldReturnTrueWhenStatusIsONLINE() {
        when(hashOperations.get(PRESENCE_1, "status")).thenReturn("ONLINE");

        assertThat(presenceService.isOnline("1")).isTrue();
    }

    @Test
    void isOnline_ShouldReturnTrueWhenStatusIsIN_ROOM() {
        when(hashOperations.get(PRESENCE_1, "status")).thenReturn("IN_ROOM");

        assertThat(presenceService.isOnline("1")).isTrue();
    }

    @Test
    void isOnline_ShouldReturnFalseWhenStatusIsAbsent() {
        when(hashOperations.get(PRESENCE_1, "status")).thenReturn(null);

        assertThat(presenceService.isOnline("1")).isFalse();
    }

    @Test
    void isInRoom_ShouldReturnTrueWhenStatusIsIN_ROOM() {
        when(hashOperations.get(PRESENCE_1, "status")).thenReturn("IN_ROOM");

        assertThat(presenceService.isInRoom("1")).isTrue();
    }

    @Test
    void isInRoom_ShouldReturnFalseWhenStatusIsONLINE() {
        when(hashOperations.get(PRESENCE_1, "status")).thenReturn("ONLINE");

        assertThat(presenceService.isInRoom("1")).isFalse();
    }

    // ── getOnlineUserCount ────────────────────────────────────────────────────────

    @Test
    void getOnlineUserCount_ShouldReturnSizeFromRedisSet() {
        when(setOperations.size(ONLINE_USERS)).thenReturn(5L);

        assertThat(presenceService.getOnlineUserCount()).isEqualTo(5L);
    }
}
