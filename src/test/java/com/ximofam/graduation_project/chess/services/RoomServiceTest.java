package com.ximofam.graduation_project.chess.services;

import com.ximofam.graduation_project.chess.dtos.request.CreateRoomRequest;
import com.ximofam.graduation_project.chess.dtos.request.JoinRoomRequest;
import com.ximofam.graduation_project.chess.dtos.response.RoomResponse;
import com.ximofam.graduation_project.chess.models.RoomSettings;
import com.ximofam.graduation_project.common.exceptions.http.ForbiddenException;
import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import com.ximofam.graduation_project.users.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private UserService userService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private RedisScript<Long> createRoomScript;
    @SuppressWarnings("rawtypes") @Mock private RedisScript searchLobbyScript;
    @SuppressWarnings("rawtypes") @Mock private RedisScript joinRoomScript;
    @SuppressWarnings("rawtypes") @Mock private RedisScript leaveRoomScript;
    @Mock private DefaultRedisScript<Long> presenceSetStatusScript;
    @Mock private HashOperations<String, Object, Object> hashOperations;

    private RoomService roomService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // Manual construction avoids Mockito generic-type confusion with multiple RedisScript fields
        roomService = new RoomService(redisTemplate, userService, messagingTemplate,
                createRoomScript, searchLobbyScript, joinRoomScript, leaveRoomScript,
                presenceSetStatusScript);
    }

    // ── createRoom ───────────────────────────────────────────────────────────────

    @Test
    void createRoom_ShouldReturnRoomResponseAndBroadcastToLobby() {
        UserSimpleResponse host = new UserSimpleResponse();
        when(userService.getUserSimpleResponseById(1L)).thenReturn(host);

        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Test Room");
        req.setSettings(new RoomSettings());

        RoomResponse result = roomService.createRoom("1", req);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Room");
        assertThat(result.getRoomId()).isNotNull();
        assertThat(result.getHost()).isEqualTo(host);

        verify(redisTemplate).execute(eq(createRoomScript), anyList(), any(), any(), any(), any(), any());
        verify(messagingTemplate).convertAndSend(eq("/topic/lobbies"), (Object) any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createRoom_ShouldPassCorrectRedisKeys() {
        when(userService.getUserSimpleResponseById(2L)).thenReturn(new UserSimpleResponse());

        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Key Check");
        req.setSettings(new RoomSettings());

        RoomResponse result = roomService.createRoom("2", req);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(eq(createRoomScript), keysCaptor.capture(), any(), any(), any(), any(), any());

        List<String> keys = keysCaptor.getValue();
        assertThat(keys).hasSize(3);
        assertThat(keys.get(0)).isEqualTo("room:" + result.getRoomId());
        assertThat(keys.get(1)).isEqualTo("room:idx:lobby");
        assertThat(keys.get(2)).isEqualTo("user:2:presence");
    }

    // ── leaveRoom ────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void leaveRoom_HostLeft_ShouldResetOthersPresenceAndBroadcastRoomDeleted() {
        when(redisTemplate.execute(eq(leaveRoomScript), anyList(), any(), any()))
                .thenReturn(List.of(0L, "HOST_LEFT", "white", List.of("42")));

        roomService.leaveRoom("room1", "1");

        // presence reset for "42" (other player) and "1" (host)
        verify(redisTemplate, times(2)).execute(eq(presenceSetStatusScript), anyList(),
                eq("ONLINE"), eq("roomId"), eq("isHost"), eq("role"));
        verify(messagingTemplate).convertAndSend(eq("/topic/lobbies"), (Object) any());
        verify(messagingTemplate).convertAndSend(eq("/topic/room/room1"), (Object) any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void leaveRoom_PlayerLeft_ShouldResetPresenceAndBroadcastPlayerLeftAndRoomUpdated() {
        when(redisTemplate.execute(eq(leaveRoomScript), anyList(), any(), any()))
                .thenReturn(List.of(0L, "PLAYER_LEFT", "black", List.of()));

        roomService.leaveRoom("room1", "99");

        verify(redisTemplate).execute(eq(presenceSetStatusScript), anyList(),
                eq("ONLINE"), eq("roomId"), eq("isHost"), eq("role"));
        verify(messagingTemplate).convertAndSend(eq("/topic/room/room1"), (Object) any());
        verify(messagingTemplate).convertAndSend(eq("/topic/lobbies"), (Object) any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void leaveRoom_SpectatorLeft_ShouldOnlyBroadcastPlayerLeft() {
        when(redisTemplate.execute(eq(leaveRoomScript), anyList(), any(), any()))
                .thenReturn(List.of(0L, "SPECTATOR_LEFT", "spectator", List.of()));

        roomService.leaveRoom("room1", "5");

        verify(redisTemplate, never()).execute(eq(presenceSetStatusScript), anyList(), any(), any(), any(), any());
        verify(messagingTemplate).convertAndSend(eq("/topic/room/room1"), (Object) any());
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/lobbies"), (Object) any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void leaveRoom_RoomNotFound_ShouldThrowNotFoundException() {
        when(redisTemplate.execute(eq(leaveRoomScript), anyList(), any(), any()))
                .thenReturn(java.util.Arrays.asList(-1L, null, null, List.of()));

        assertThatThrownBy(() -> roomService.leaveRoom("room1", "1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void leaveRoom_UserNotInRoom_ShouldThrowForbiddenException() {
        when(redisTemplate.execute(eq(leaveRoomScript), anyList(), any(), any()))
                .thenReturn(java.util.Arrays.asList(-3L, null, null, List.of()));

        assertThatThrownBy(() -> roomService.leaveRoom("room1", "1"))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── joinRoom ─────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void joinRoom_InvalidRole_ShouldThrowBadRequest() {
        when(redisTemplate.execute(eq(joinRoomScript), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(-6L));

        JoinRoomRequest req = new JoinRoomRequest();
        req.setRole("invalid");

        assertThatThrownBy(() -> roomService.joinRoom("room1", "1", req))
                .isInstanceOf(com.ximofam.graduation_project.common.exceptions.http.BadRequestException.class)
                .hasMessageContaining("Invalid role");
    }

    // ── isMember ─────────────────────────────────────────────────────────────────

    @Test
    void isMember_WhenUserIsHost_ShouldReturnTrue() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.multiGet(eq("room:room1"), anyList()))
                .thenReturn(List.of("7", "", ""));

        assertThat(roomService.isMember("room1", "7")).isTrue();
    }

    @Test
    void isMember_WhenUserNotPresent_ShouldReturnFalse() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.multiGet(eq("room:room1"), anyList()))
                .thenReturn(List.of("1", "2", "3"));

        assertThat(roomService.isMember("room1", "99")).isFalse();
    }
}
