package com.ximofam.graduation_project.chess.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ximofam.graduation_project.chess.dtos.request.CreateRoomRequest;
import com.ximofam.graduation_project.chess.enums.RoomStatus;
import com.ximofam.graduation_project.common.utils.RedisKeys;
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

    public String createRoom(String hostId, CreateRoomRequest request) {
        String roomId = UUID.randomUUID().toString();
        String roomKey = RedisKeys.roomInfo(roomId);
        String userPresenceKey = RedisKeys.presenceUser(hostId);
        long createdAt = Instant.now().toEpochMilli();

        UserSimpleResponse hostInfo = userService.getUserSimpleResponseById(Long.parseLong(hostId));

        String settingsJson;
        try {
            settingsJson = objectMapper.writeValueAsString(request.getSettings());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize room data", e);
        }

        redisTemplate.execute(
                createRoomScript,
                List.of(roomKey, RedisKeys.LOBBY_INDEX, userPresenceKey),
                hostId,
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
        roomData.put("status", RoomStatus.WAITING.name());

        messagingTemplate.convertAndSend("/topic/lobbies",
                (Object) Map.of("type", "ROOM_CREATED", "data", roomData)
        );

        return roomId;
    }

    public Map<String, Object> getLobbyRooms(int page, int size) {
        long start = (long) page * size;
        long end = start + size - 1;

        Long totalElements = redisTemplate.opsForZSet().zCard(RedisKeys.LOBBY_INDEX);
        if (totalElements == null) totalElements = 0L;

        List<Map<String, Object>> content = new ArrayList<>();
        if (totalElements > 0) {
            Set<String> roomIds = redisTemplate.opsForZSet().reverseRange(RedisKeys.LOBBY_INDEX, start, end);
            if (roomIds != null && !roomIds.isEmpty()) {
                Set<Long> userIdsToFetch = new HashSet<>();
                List<Map<Object, Object>> rawRooms = new ArrayList<>();
                List<String> validRoomIds = new ArrayList<>();

                for (String id : roomIds) {
                    Map<Object, Object> raw = redisTemplate.opsForHash().entries(RedisKeys.roomInfo(id));
                    if (raw.isEmpty()) continue;

                    rawRooms.add(raw);
                    validRoomIds.add(id);

                    for (String role : new String[]{"host", "white", "black"}) {
                        Object uId = raw.get(role);
                        if (uId != null && !uId.toString().isBlank()) {
                            try {
                                userIdsToFetch.add(Long.parseLong(uId.toString()));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }

                Map<Long, UserSimpleResponse> hydratedUsers = userIdsToFetch.isEmpty()
                        ? Collections.emptyMap()
                        : userService.getUsersSimpleResponseByIds(userIdsToFetch);

                for (int i = 0; i < rawRooms.size(); i++) {
                    content.add(hydrateRoom(validRoomIds.get(i), rawRooms.get(i), hydratedUsers));
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

    public boolean isMember(String roomId, String userId) {
        String roomKey = RedisKeys.roomInfo(roomId);
        List<Object> userIds = redisTemplate.opsForHash().multiGet(roomKey, Arrays.asList("host", "white", "black"));
        if (userIds == null) return false;

        for (Object id : userIds) {
            if (id != null && userId.equals(id.toString())) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Object> getRoomDetails(String roomId) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(RedisKeys.roomInfo(roomId));
        if (raw.isEmpty()) {
            return null;
        }

        Set<String> spectatorIdsStr = redisTemplate.opsForZSet().reverseRange(RedisKeys.roomSpectators(roomId), 0, -1);
        List<String> spectatorIds = (spectatorIdsStr != null) ? new ArrayList<>(spectatorIdsStr) : new ArrayList<>();
        raw.put("spectators", spectatorIds);

        Set<Long> userIdsToFetch = new HashSet<>();
        for (String role : new String[]{"host", "white", "black"}) {
            Object uId = raw.get(role);
            if (uId != null && !uId.toString().isBlank()) {
                try {
                    userIdsToFetch.add(Long.parseLong(uId.toString()));
                } catch (Exception ignored) {
                }
            }
        }

        for (String spId : spectatorIds) {
            try {
                userIdsToFetch.add(Long.parseLong(spId));
            } catch (Exception ignored) {
            }
        }

        Map<Long, UserSimpleResponse> hydratedUsers = userIdsToFetch.isEmpty()
                ? Collections.emptyMap()
                : userService.getUsersSimpleResponseByIds(userIdsToFetch);

        return hydrateRoom(roomId, raw, hydratedUsers);
    }

    private Map<String, Object> hydrateRoom(String roomId, Map<Object, Object> raw, Map<Long, UserSimpleResponse> hydratedUsers) {
        Map<String, Object> room = new HashMap<>();
        room.put("roomId", roomId);

        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            String key = entry.getKey().toString();
            Object valueObj = entry.getValue();

            switch (key) {
                case "settings" -> {
                    try {
                        room.put(key, objectMapper.readValue(valueObj.toString(), Map.class));
                    } catch (Exception e) {
                        room.put(key, valueObj);
                    }
                }
                case "host", "white", "black" -> {
                    if (valueObj == null || valueObj.toString().isBlank()) {
                        room.put(key, null);
                    } else {
                        try {
                            room.put(key, hydratedUsers.get(Long.parseLong(valueObj.toString())));
                        } catch (Exception e) {
                            room.put(key, null);
                        }
                    }
                }
                case "spectators" -> {
                    @SuppressWarnings("unchecked")
                    List<String> spIds = (List<String>) valueObj;
                    List<UserSimpleResponse> hydratedSpectators = new ArrayList<>();
                    for (String spId : spIds) {
                        try {
                            UserSimpleResponse u = hydratedUsers.get(Long.parseLong(spId));
                            if (u != null) hydratedSpectators.add(u);
                        } catch (Exception ignored) {
                        }
                    }
                    room.put(key, hydratedSpectators);
                }
                default -> room.put(key, valueObj);
            }
        }
        return room;
    }
}
