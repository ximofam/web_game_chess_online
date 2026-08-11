package com.ximofam.graduation_project.chess.services;

import com.ximofam.graduation_project.chess.dtos.models.RoomSettings;
import com.ximofam.graduation_project.chess.dtos.request.CreateRoomRequest;
import com.ximofam.graduation_project.chess.dtos.request.JoinRoomRequest;
import com.ximofam.graduation_project.chess.dtos.response.RoomResponse;
import com.ximofam.graduation_project.chess.dtos.ws.ChatMessagePayload;
import com.ximofam.graduation_project.common.exceptions.http.ForbiddenException;
import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.common.utils.TopicUtils;
import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import com.ximofam.graduation_project.users.services.PresenceService;
import com.ximofam.graduation_project.users.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RedisScript<Long> createRoomScript;
    @Mock
    private RedisScript<List<Object>> searchLobbyScript;
    @Mock
    private RedisScript<Long> joinRoomScript;
    @Mock
    private RedisScript<List<Object>> leaveRoomScript;
    @SuppressWarnings("rawtypes")
    @Mock
    private RedisScript deleteRoomScript;
    @Mock
    private RedisScript<List<Object>> switchSeatScript;
    @Mock
    private UserService userService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private PresenceService presenceService;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private ListOperations<String, String> listOperations;
    @org.mockito.Spy
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private com.ximofam.graduation_project.chess.mappers.RoomMapper roomMapper;

    @Mock private com.ximofam.graduation_project.chess.services.GameService gameService;
    @Mock private org.redisson.api.RedissonClient redissonClient;
    @Mock private org.redisson.api.RLock rLock;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private RoomService roomService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        roomMapper = spy(new com.ximofam.graduation_project.chess.mappers.RoomMapper(objectMapper));
        // Manual construction avoids Mockito generic-type confusion with multiple RedisScript fields
        roomService = new RoomService(redisTemplate, userService, messagingTemplate,
                createRoomScript, searchLobbyScript, joinRoomScript, leaveRoomScript, deleteRoomScript, switchSeatScript,
                objectMapper, roomMapper, gameService, redissonClient, eventPublisher);
        
        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            lenient().when(rLock.tryLock(anyLong(), anyLong(), any(java.util.concurrent.TimeUnit.class))).thenReturn(true);
        } catch (InterruptedException e) {
            // ignore
        }
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);

        lenient().when(redisTemplate.execute(eq(createRoomScript), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(1L);
    }

    // ── createRoom ───────────────────────────────────────────────────────────────

    @Test
    void createRoom_ShouldReturnRoomResponseAndBroadcastToLobby() {
        UserSimpleResponse host = new UserSimpleResponse();
        when(userService.getUserSimpleResponseById(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(host);

        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Test Room");
        req.setSettings(new RoomSettings());

        RoomResponse result = roomService.createRoom("00000000-0000-0000-0000-000000000001", req);

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
        when(userService.getUserSimpleResponseById(java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"))).thenReturn(new UserSimpleResponse());

        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Key Check");
        req.setSettings(new RoomSettings());

        RoomResponse result = roomService.createRoom("00000000-0000-0000-0000-000000000002", req);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(eq(createRoomScript), keysCaptor.capture(), any(), any(), any(), any(), any());

        List<String> keys = keysCaptor.getValue();
        assertThat(keys).hasSize(3);
        assertThat(keys.get(0)).isEqualTo("room:" + result.getRoomId());
        assertThat(keys.get(1)).isEqualTo("room:idx:lobby");
        assertThat(keys.get(2)).isEqualTo("user:00000000-0000-0000-0000-000000000002:presence");
    }

    // ── leaveRoom ────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void leaveRoom_HostTransferred_ShouldBroadcastHostTransferred() {
        when(redisTemplate.execute(eq(leaveRoomScript), anyList(), any()))
                .thenReturn(List.of(1L, "HOST_TRANSFERRED", "none", "WAITING", "00000000-0000-0000-0000-000000000002", "white"));
        when(userService.getUserSimpleResponseById(java.util.UUID.fromString("00000000-0000-0000-0000-000000000002")))
                .thenReturn(new UserSimpleResponse());
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        roomService.leaveRoom("room1", "00000000-0000-0000-0000-000000000001", com.ximofam.graduation_project.chess.enums.LeaveReason.USER_LEAVE);

        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq(TopicUtils.room("room1")), (Object) any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void leaveRoom_RoomEmpty_ShouldDeleteRoomAndBroadcast() {
        when(redisTemplate.execute(eq(leaveRoomScript), anyList(), any()))
                .thenReturn(List.of(1L, "ROOM_EMPTY", "none", "WAITING"));
        when(redisTemplate.execute(eq(deleteRoomScript), anyList(), any()))
                .thenReturn(List.of("00000000-0000-0000-0000-000000000042"));

        roomService.leaveRoom("room1", "00000000-0000-0000-0000-000000000001", com.ximofam.graduation_project.chess.enums.LeaveReason.USER_LEAVE);

        verify(eventPublisher, times(2)).publishEvent(any(com.ximofam.graduation_project.users.dtos.events.SetUserPresenceEvent.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/lobbies"), (Object) any());
        verify(messagingTemplate).convertAndSend(eq(TopicUtils.room("room1")), (Object) any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void leaveRoom_PlayerLeft_ShouldBroadcastPlayerLeftAndRoomUpdated() {
        when(redisTemplate.execute(eq(leaveRoomScript), anyList(), any()))
                .thenReturn(List.of(1L, "PLAYER_LEFT", "black"));

        roomService.leaveRoom("room1", "99", com.ximofam.graduation_project.chess.enums.LeaveReason.USER_LEAVE);

        verify(eventPublisher).publishEvent(any(com.ximofam.graduation_project.users.dtos.events.SetUserPresenceEvent.class));
        verify(messagingTemplate).convertAndSend(eq(TopicUtils.room("room1")), (Object) any());
        verify(messagingTemplate).convertAndSend(eq("/topic/lobbies"), (Object) any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void leaveRoom_SpectatorLeft_ShouldOnlyBroadcastPlayerLeft() {
        when(redisTemplate.execute(eq(leaveRoomScript), anyList(), any()))
                .thenReturn(List.of(1L, "SPECTATOR_LEFT", "spectator"));

        roomService.leaveRoom("room1", "5", com.ximofam.graduation_project.chess.enums.LeaveReason.USER_LEAVE);

        verify(eventPublisher).publishEvent(any(com.ximofam.graduation_project.users.dtos.events.SetUserPresenceEvent.class));
        verify(messagingTemplate).convertAndSend(eq(TopicUtils.room("room1")), (Object) any());
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/lobbies"), (Object) any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void leaveRoom_RoomNotFound_ShouldThrowNotFoundException() {
        when(redisTemplate.execute(eq(leaveRoomScript), anyList(), any()))
                .thenReturn(List.of(-1L));

        assertThatThrownBy(() -> roomService.leaveRoom("room1", "00000000-0000-0000-0000-000000000001",
                com.ximofam.graduation_project.chess.enums.LeaveReason.USER_LEAVE))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void leaveRoom_UserNotInRoom_ShouldThrowForbiddenException() {
        when(redisTemplate.execute(eq(leaveRoomScript), anyList(), any()))
                .thenReturn(List.of(-10L));

        assertThatThrownBy(() -> roomService.leaveRoom("room1", "00000000-0000-0000-0000-000000000001",
                com.ximofam.graduation_project.chess.enums.LeaveReason.USER_LEAVE))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── joinRoom ─────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void joinRoom_InvalidRole_ShouldThrowBadRequest() {
        when(redisTemplate.execute(eq(joinRoomScript), anyList(), any(), any(), any(), any()))
                .thenReturn(-6L);

        JoinRoomRequest req = new JoinRoomRequest();
        req.setRole("invalid");

        assertThatThrownBy(() -> roomService.joinRoom("room1", "00000000-0000-0000-0000-000000000001", req))
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
                .thenReturn(List.of("00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002", "00000000-0000-0000-0000-000000000003"));

        assertThat(roomService.isMember("room1", "99")).isFalse();
    }

    // ── sendChatMessage ───────────────────────────────────────────────────────────

    @Test
    void sendChatMessage_RoomNotFound_ShouldThrowNotFoundException() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("room:room1", "settings")).thenReturn(null);

        assertThatThrownBy(() -> roomService.sendChatMessage("room1", "00000000-0000-0000-0000-000000000001", "hi"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void sendChatMessage_ChatLocked_ShouldThrowForbiddenException() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("room:room1", "settings")).thenReturn("{\"chatLocked\":true}");

        assertThatThrownBy(() -> roomService.sendChatMessage("room1", "00000000-0000-0000-0000-000000000001", "hi"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sendChatMessage_HappyPath_ShouldPushTrimAndBroadcast() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("room:room1", "settings")).thenReturn("{}");
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        UserSimpleResponse sender = new UserSimpleResponse();
        sender.setId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
        sender.setUsername("alice");
        when(userService.getUserSimpleResponseById(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(sender);

        roomService.sendChatMessage("room1", "00000000-0000-0000-0000-000000000001", "hello");

        verify(listOperations).leftPush(eq("room:room1:chat"), anyString());
        verify(listOperations).trim("room:room1:chat", 0, 9);
        verify(messagingTemplate).convertAndSend(eq(TopicUtils.room("room1")), (Object) any());
    }

    // ── getChatHistory ────────────────────────────────────────────────────────────

    @Test
    void getChatHistory_Empty_ShouldReturnEmptyList() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("room:room1:chat", 0, -1)).thenReturn(List.of());

        assertThat(roomService.getChatHistory("room1")).isEmpty();
    }

    @Test
    void getChatHistory_ShouldReturnChronologicalOrder() {
        // Redis list: newest first (leftPush order) → [msg2_json, msg1_json]
        String msg1 = "{\"sender\":{\"id\":\"00000000-0000-0000-0000-000000000001\",\"username\":\"a\",\"avatarUrl\":null},\"message\":\"first\",\"sentAt\":1000}";
        String msg2 = "{\"sender\":{\"id\":\"00000000-0000-0000-0000-000000000001\",\"username\":\"a\",\"avatarUrl\":null},\"message\":\"second\",\"sentAt\":2000}";

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("room:room1:chat", 0, -1)).thenReturn(List.of(msg2, msg1));

        List<ChatMessagePayload> result = roomService.getChatHistory("room1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).message()).isEqualTo("first");  // oldest first
        assertThat(result.get(1).message()).isEqualTo("second");
    }

    @Test
    void getChatHistory_ShouldSkipInvalidJson() {
        String valid = "{\"sender\":{\"id\":\"00000000-0000-0000-0000-000000000001\",\"username\":\"a\",\"avatarUrl\":null},\"message\":\"ok\",\"sentAt\":1000}";
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("room:room1:chat", 0, -1)).thenReturn(List.of("not-json", valid));

        List<ChatMessagePayload> result = roomService.getChatHistory("room1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).message()).isEqualTo("ok");
    }
}
