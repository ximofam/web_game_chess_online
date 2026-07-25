package com.ximofam.graduation_project.chess.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ximofam.graduation_project.chess.dtos.request.CreateRoomRequest;
import com.ximofam.graduation_project.chess.dtos.response.RoomResponse;
import com.ximofam.graduation_project.chess.enums.RoomStatus;
import com.ximofam.graduation_project.chess.models.RoomSettings;
import com.ximofam.graduation_project.common.utils.RedisKeys;
import com.ximofam.graduation_project.common.utils.Utils;
import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import com.ximofam.graduation_project.users.services.UserService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
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
    private final RedisScript<List> searchLobbyScript = RedisScript.of(new ClassPathResource("scripts/search_lobby.lua"), List.class);

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

        RoomResponse roomData = new RoomResponse();
        roomData.setRoomId(roomId);
        roomData.setHost(hostInfo);
        roomData.setName(request.getName());
        roomData.setCreatedAt(createdAt);
        roomData.setSettings(request.getSettings());
        roomData.setWhite(hostInfo);
        roomData.setBlack(null);
        roomData.setStatus(RoomStatus.WAITING.name());
        roomData.setHostId(hostId);
        roomData.setSpectators(List.of());

        messagingTemplate.convertAndSend("/topic/lobbies",
                (Object) Map.of("type", "ROOM_CREATED", "data", roomData)
        );

        return roomId;
    }

    public Map<String, Object> getLobbyRooms(String query, int page, int size) {
        String safeQuery = query == null ? "" : query.trim().toLowerCase();
        long start = (long) page * size;
        long end = start + size - 1;

        long totalElements;
        List<String> roomIds;

        if (safeQuery.isEmpty()) {
            Long count = redisTemplate.opsForZSet().zCard(RedisKeys.LOBBY_INDEX);
            totalElements = count == null ? 0L : count;
            Set<String> ids = redisTemplate.opsForZSet().reverseRange(RedisKeys.LOBBY_INDEX, start, end);
            roomIds = ids == null ? List.of() : new ArrayList<>(ids);
        } else {
            List<Object> searchResult = redisTemplate.execute(
                    searchLobbyScript,
                    List.of(RedisKeys.LOBBY_INDEX),
                    safeQuery,
                    String.valueOf(start),
                    String.valueOf(end)
            );
            totalElements = 0L;
            roomIds = List.of();
            if (searchResult != null && searchResult.size() == 2) {
                totalElements = ((Number) searchResult.get(0)).longValue();
                @SuppressWarnings("unchecked")
                List<String> ids = (List<String>) searchResult.get(1);
                roomIds = ids;
            }
        }

        List<RoomResponse> content = roomIds.isEmpty() ? List.of() : hydrateRooms(roomIds);
        return buildPagedResult(content, page, size, totalElements);
    }

    private List<RoomResponse> hydrateRooms(List<String> roomIds) {
        List<Object> pipelinedResults = redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(@NonNull RedisOperations operations) {
                for (String id : roomIds) {
                    operations.opsForHash().entries(RedisKeys.roomInfo(id));
                }
                return null;
            }
        });

        Map<String, Map<Object, Object>> rawByRoomId = new LinkedHashMap<>();
        Set<Long> userIdsToFetch = new HashSet<>();
        for (int i = 0; i < roomIds.size(); i++) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> raw = (Map<Object, Object>) pipelinedResults.get(i);
            if (raw == null || raw.isEmpty()) continue;

            rawByRoomId.put(roomIds.get(i), raw);
            collectParticipantIds(raw, userIdsToFetch);
        }

        Map<Long, UserSimpleResponse> hydratedUsers = userIdsToFetch.isEmpty()
                ? Collections.emptyMap()
                : userService.getUsersSimpleResponseByIds(userIdsToFetch);

        List<RoomResponse> content = new ArrayList<>(rawByRoomId.size());
        for (Map.Entry<String, Map<Object, Object>> entry : rawByRoomId.entrySet()) {
            content.add(buildRoomResponse(entry.getKey(), entry.getValue(), List.of(), hydratedUsers));
        }
        return content;
    }

    private Map<String, Object> buildPagedResult(List<RoomResponse> content, int page, int size, long totalElements) {
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

    public RoomResponse getRoomDetails(String roomId) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(RedisKeys.roomInfo(roomId));
        if (raw.isEmpty()) return null;

        Set<String> spectatorIdsStr = redisTemplate.opsForZSet().reverseRange(RedisKeys.roomSpectators(roomId), 0, -1);
        List<String> spectatorIds = spectatorIdsStr != null ? new ArrayList<>(spectatorIdsStr) : List.of();

        Set<Long> userIdsToFetch = new HashSet<>();
        collectParticipantIds(raw, userIdsToFetch);
        for (String spId : spectatorIds) {
            try { userIdsToFetch.add(Long.parseLong(spId)); } catch (NumberFormatException ignored) {}
        }

        Map<Long, UserSimpleResponse> users = userIdsToFetch.isEmpty()
                ? Collections.emptyMap()
                : userService.getUsersSimpleResponseByIds(userIdsToFetch);

        List<UserSimpleResponse> spectators = spectatorIds.stream()
                .map(id -> { try { return users.get(Long.parseLong(id)); } catch (Exception e) { return null; } })
                .filter(Objects::nonNull)
                .toList();

        return buildRoomResponse(roomId, raw, spectators, users);
    }

    private RoomResponse buildRoomResponse(String roomId, Map<Object, Object> raw,
                                           List<UserSimpleResponse> spectators,
                                           Map<Long, UserSimpleResponse> users) {
        String hostId = Utils.str(raw, "host");

        RoomResponse response = new RoomResponse();
        response.setRoomId(roomId);
        response.setName(Utils.str(raw, "name"));
        response.setStatus(Utils.str(raw, "status"));
        response.setHostId(hostId);
        response.setHost(resolveUser(hostId, users));
        response.setWhite(resolveUser(Utils.str(raw, "white"), users));
        response.setBlack(resolveUser(Utils.str(raw, "black"), users));
        response.setSpectators(spectators);
        response.setCreatedAt(Utils.parseLong(raw, "createdAt"));
        response.setSettings(parseSettings(raw.get("settings")));
        return response;
    }

    private static RoomSettings parseSettings(Object value) {
        if (value == null) return new RoomSettings();
        try {
            return objectMapper.readValue(value.toString(), RoomSettings.class);
        } catch (Exception e) {
            return new RoomSettings();
        }
    }

    private static UserSimpleResponse resolveUser(String id, Map<Long, UserSimpleResponse> users) {
        if (id == null) return null;
        try {
            return users.get(Long.parseLong(id));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void collectParticipantIds(Map<Object, Object> raw, Set<Long> target) {
        for (String role : new String[]{"host", "white", "black"}) {
            String id = Utils.str(raw, role);
            if (id != null) {
                try {
                    target.add(Long.parseLong(id));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }
}
