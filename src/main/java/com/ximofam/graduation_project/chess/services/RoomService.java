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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final StringRedisTemplate redisTemplate;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
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
                request.getName()
        );

        java.util.Map<String, Object> roomData = new java.util.HashMap<>();
        roomData.put("roomId", roomId);
        roomData.put("host", hostInfo);
        roomData.put("name", request.getName());
        roomData.put("createdAt", createdAt);
        roomData.put("settings", request.getSettings());
        roomData.put("white", hostInfo);
        roomData.put("black", null);
        roomData.put("status", "WAITING");

        messagingTemplate.convertAndSend("/topic/lobbies",
                (Object) java.util.Map.of("type", "ROOM_CREATED", "data", roomData)
        );

        return roomId;
    }

    public Map<String, Object> getLobbyRooms(int page, int size) {
        long start = (long) page * size;
        long end = start + size - 1;

        Long totalElements = redisTemplate.opsForZSet().zCard(LOBBY_KEY);
        if (totalElements == null) totalElements = 0L;

        List<Map<String, Object>> content = new ArrayList<>();
        if (totalElements > 0) {
            Set<String> roomIds = redisTemplate.opsForZSet().reverseRange(LOBBY_KEY, start, end);
            if (roomIds != null && !roomIds.isEmpty()) {
                for (String id : roomIds) {
                    Map<Object, Object> raw = redisTemplate.opsForHash().entries("room:" + id);
                    if (raw.isEmpty()) continue;

                    Map<String, Object> room = new HashMap<>();
                    room.put("roomId", id);
                    for (Map.Entry<Object, Object> entry : raw.entrySet()) {
                        String key = entry.getKey().toString();
                        String value = entry.getValue().toString();
                        try {
                            if (key.equals("host") || key.equals("settings") || key.equals("white") || key.equals("black")) {
                                if (value == null || value.isBlank()) {
                                    room.put(key, null); // Explicitly send null so frontend knows the seat is empty
                                } else {
                                    room.put(key, objectMapper.readValue(value, Map.class));
                                }
                            } else {
                                room.put(key, value);
                            }
                        } catch (Exception e) {
                            room.put(key, value);
                        }
                    }
                    content.add(room);
                }
            }
        }

        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        Map<String, Object> pageMeta = new HashMap<>();
        pageMeta.put("size", size);
        pageMeta.put("number", page);
        pageMeta.put("totalElements", totalElements);
        pageMeta.put("totalPages", totalPages);

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("page", pageMeta);

        return result;
    }
}
