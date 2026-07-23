package com.ximofam.graduation_project.chess.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ximofam.graduation_project.chess.dtos.request.CreateRoomRequest;
import com.ximofam.graduation_project.chess.models.RoomSettings;
import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import com.ximofam.graduation_project.users.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private UserService userService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RoomService roomService;

    @Test
    void createRoom_ShouldExecuteRedisScriptWithCorrectArguments() throws JsonProcessingException {
        // Arrange
        String hostId = "123";
        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Ponytail Room");
        RoomSettings settings = new RoomSettings();
        request.setSettings(settings);

        UserSimpleResponse mockUser = new UserSimpleResponse();

        String expectedSettingsJson = "{\"chatLocked\":false}";
        String expectedHostJson = "{\"id\":123,\"name\":\"host\"}";

        when(userService.getUserSimpleResponseById(123L)).thenReturn(mockUser);
        when(objectMapper.writeValueAsString(settings)).thenReturn(expectedSettingsJson);
        when(objectMapper.writeValueAsString(mockUser)).thenReturn(expectedHostJson);

        // Act
        String roomId = roomService.createRoom(hostId, request);

        // Assert
        assertNotNull(roomId, "Room ID should not be null");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        
        verify(redisTemplate).execute(
                any(RedisScript.class),
                keysCaptor.capture(),
                eq(expectedHostJson),
                eq(expectedSettingsJson),
                any(String.class), // createdAt timestamp
                eq(roomId),
                eq("Ponytail Room")
        );

        List<String> keys = keysCaptor.getValue();
        assertEquals(3, keys.size(), "Should pass exactly 3 Redis keys");
        assertEquals("room:" + roomId, keys.get(0), "Room key prefix should match");
        assertEquals("rooms:lobby", keys.get(1), "Lobby key should match");
        assertEquals("user:123:rooms", keys.get(2), "User rooms key should match");
    }
}
