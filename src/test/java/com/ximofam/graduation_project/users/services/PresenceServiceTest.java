package com.ximofam.graduation_project.users.services;

import com.ximofam.graduation_project.common.helpers.dtos.WsEvent;
import com.ximofam.graduation_project.users.dtos.events.UserPresenceChangedEvent;
import com.ximofam.graduation_project.users.dtos.events.UserWentOfflineEvent;
import com.ximofam.graduation_project.users.dtos.ws.UserPresenceEvent;
import com.ximofam.graduation_project.users.enums.PresenceStatus;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock rLock;

    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private SetOperations<String, String> setOperations;

    @Mock private RedisScript<Long> presenceConnectScript;
    @Mock private RedisScript<Long> handlePlayingDisconnectScript;
    @SuppressWarnings("rawtypes") @Mock private RedisScript presenceDisconnectScript;

    private PresenceService presenceService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws InterruptedException {
        presenceService = new PresenceService(
                redisTemplate, userRepository, messagingTemplate, eventPublisher, redissonClient,
                presenceConnectScript, handlePlayingDisconnectScript, presenceDisconnectScript
        );

        ReflectionTestUtils.setField(presenceService, "sessionTtl", Duration.ofSeconds(30));
        ReflectionTestUtils.setField(presenceService, "offlineGracePeriod", Duration.ofSeconds(5));

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);

        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        lenient().when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SESSION_ID = "session1";
    private static final String SESSIONS_KEY = "user:" + USER_ID + ":sessions";
    private static final String PRESENCE_KEY = "user:" + USER_ID + ":presence";
    private static final String SESSION_DETAIL_KEY = "user:" + USER_ID + ":session:" + SESSION_ID;
    private static final String ONLINE_USERS = "sys:online_users";

    @Test
    void handleConnect_FirstSession_ShouldBroadcastAndPublishEvent() {
        when(redisTemplate.execute(eq(presenceConnectScript), anyList(), anyString(), anyString(), anyString())).thenReturn(1L);
        when(setOperations.size(ONLINE_USERS)).thenReturn(1L);

        presenceService.handleConnect(USER_ID, SESSION_ID);

        verify(eventPublisher).publishEvent(any(UserPresenceChangedEvent.class));
        verify(messagingTemplate).convertAndSend("/topic/presence.online-count", 1L);
    }

    @Test
    void handleConnect_AlreadyOnline_ShouldNotBroadcast() {
        when(redisTemplate.execute(eq(presenceConnectScript), anyList(), anyString(), anyString(), anyString())).thenReturn(0L);

        presenceService.handleConnect(USER_ID, SESSION_ID);

        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleDisconnect_LastSession_ShouldUpdateLastSeenAndPublishEvents() {
        when(redisTemplate.execute(eq(presenceDisconnectScript), anyList(), eq(SESSION_ID), eq(USER_ID)))
                .thenReturn(List.of(1L, List.of("status", "IN_ROOM")));
        when(setOperations.size(ONLINE_USERS)).thenReturn(0L);

        presenceService.handleDisconnect(USER_ID, SESSION_ID);

        verify(userRepository).updateLastSeen(eq(UUID.fromString(USER_ID)), any(Instant.class));
        verify(redisTemplate).expire(PRESENCE_KEY, Duration.ofSeconds(5)); // Due to IN_ROOM
        verify(redisTemplate).delete(SESSION_DETAIL_KEY);

        verify(eventPublisher).publishEvent(any(UserPresenceChangedEvent.class));
        verify(eventPublisher).publishEvent(any(UserWentOfflineEvent.class));
        verify(messagingTemplate).convertAndSend("/topic/presence.online-count", 0L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleDisconnect_NotLastSession_ShouldOnlyDeleteSessionDetail() {
        when(redisTemplate.execute(eq(presenceDisconnectScript), anyList(), eq(SESSION_ID), eq(USER_ID)))
                .thenReturn(List.of(0L));

        presenceService.handleDisconnect(USER_ID, SESSION_ID);

        verifyNoInteractions(userRepository);
        verify(redisTemplate).delete(SESSION_DETAIL_KEY);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void handleHeartbeat_ShouldExtendSessionTTL() {
        presenceService.handleHeartbeat(USER_ID, SESSION_ID);
        verify(redisTemplate).expire(SESSION_DETAIL_KEY, Duration.ofSeconds(30));
    }

    @Test
    void isOnline_ShouldReturnTrueWhenStatusIsONLINE() {
        when(hashOperations.get(PRESENCE_KEY, "status")).thenReturn("ONLINE");
        assertThat(presenceService.isOnline(USER_ID)).isTrue();
    }

    @Test
    void isInRoom_ShouldReturnTrueWhenStatusIsIN_ROOM() {
        when(hashOperations.get(PRESENCE_KEY, "status")).thenReturn("IN_ROOM");
        assertThat(presenceService.isInRoom(USER_ID)).isTrue();
    }
}
