package com.ximofam.graduation_project.chess.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ximofam.graduation_project.chess.dtos.request.CreateRoomRequest;
import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import com.ximofam.graduation_project.users.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final StringRedisTemplate redisTemplate;
    private final UserService userService;
    private final RedisScript<Long> createRoomScript = RedisScript.of(new ClassPathResource("scripts/create_room.lua"), Long.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String ROOM_KEY_PREFIX = "room:";
    private static final String LOBBY_KEY = "rooms:lobby";
    private static final String USER_ROOMS_PREFIX = "user:%s:rooms";

    public String createRoom(String hostId, CreateRoomRequest request) {
        String roomId = UUID.randomUUID().toString();
        String roomKey = ROOM_KEY_PREFIX + roomId;
        String userRoomsKey = String.format(USER_ROOMS_PREFIX, hostId);
        long createdAt = Instant.now().toEpochMilli();

        UserSimpleResponse hostInfo = userService.getUserSimpleResponseById(Long.parseLong(hostId));

        String settingsJson;
        String hostInfoJson;
        try {
            settingsJson = objectMapper.writeValueAsString(request.getSettings());
            hostInfoJson = objectMapper.writeValueAsString(hostInfo);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize room data", e);
        }

        redisTemplate.execute(
                createRoomScript,
                List.of(roomKey, LOBBY_KEY, userRoomsKey),
                hostInfoJson,
                settingsJson,
                String.valueOf(createdAt),
                roomId,
                request.getName() != null ? request.getName() : ""
        );

        return roomId;
    }
}
