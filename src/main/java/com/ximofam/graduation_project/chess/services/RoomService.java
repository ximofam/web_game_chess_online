package com.ximofam.graduation_project.chess.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ximofam.graduation_project.chess.dtos.request.CreateRoomRequest;
import com.ximofam.graduation_project.chess.dtos.request.JoinRoomRequest;
import com.ximofam.graduation_project.chess.dtos.response.RoomResponse;
import com.ximofam.graduation_project.chess.enums.RoomStatus;
import com.ximofam.graduation_project.chess.models.RoomSettings;
import com.ximofam.graduation_project.common.events.UserWentOfflineEvent;
import com.ximofam.graduation_project.common.exceptions.http.BadRequestException;
import com.ximofam.graduation_project.common.exceptions.http.ForbiddenException;
import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.common.utils.RedisKeys;
import com.ximofam.graduation_project.common.utils.Utils;
import com.ximofam.graduation_project.common.ws.*;
import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import com.ximofam.graduation_project.users.enums.PresenceStatus;
import com.ximofam.graduation_project.users.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {
    private final StringRedisTemplate redisTemplate;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisScript<Long> createRoomScript = RedisScript.of(new ClassPathResource("scripts/create_room.lua"), Long.class);
    private final RedisScript<List> searchLobbyScript = RedisScript.of(new ClassPathResource("scripts/search_lobby.lua"), List.class);
    private final RedisScript<List> joinRoomScript = RedisScript.of(new ClassPathResource("scripts/join_room.lua"), List.class);
    private final RedisScript<List> leaveRoomScript = RedisScript.of(new ClassPathResource("scripts/leave_room.lua"), List.class);
    private final DefaultRedisScript<Long> presenceSetStatusScript;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public RoomResponse createRoom(String hostId, CreateRoomRequest request) {
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

        messagingTemplate.convertAndSend("/topic/lobbies", new WsEvent<>("ROOM_CREATED", roomData));

        return roomData;
    }

    @SuppressWarnings("unchecked")
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
                roomIds = (List<String>) searchResult.get(1);
            }
        }

        List<RoomResponse> content = roomIds.isEmpty() ? List.of() : hydrateRooms(roomIds);
        return buildPagedResult(content, page, size, totalElements);
    }

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    public RoomResponse joinRoom(String roomId, String userId, JoinRoomRequest request) {
        String role = request.getRole();
        long now = Instant.now().toEpochMilli();

        List<Object> result = redisTemplate.execute(
                joinRoomScript,
                List.of(RedisKeys.roomInfo(roomId), RedisKeys.presenceUser(userId), RedisKeys.roomSpectators(roomId)),
                userId, roomId, role, String.valueOf(now)
        );

        if (result == null) throw new RuntimeException("Lua script returned null");

        switch (((Number) result.getFirst()).intValue()) {
            case -1 -> throw new BadRequestException("Room not found.");
            case -2 -> throw new BadRequestException("Room is not accepting players.");
            case -3 -> throw new BadRequestException("You are already seated in this room.");
            case -4 -> throw new BadRequestException("The " + role + " seat is already taken.");
            case -5 -> throw new BadRequestException("Spectators are not allowed in this room.");
            case -6 -> throw new BadRequestException("Invalid role.");
        }

        UserSimpleResponse userInfo = userService.getUserSimpleResponseById(Long.parseLong(userId));
        messagingTemplate.convertAndSend("/topic/room/" + roomId,
                new WsEvent<>("PLAYER_JOINED", new PlayerJoinedPayload(role, userInfo)));

        if (!"spectator".equals(role)) {
            messagingTemplate.convertAndSend("/topic/lobbies",
                    new WsEvent<>("ROOM_UPDATED", new RoomUpdatedPayload(roomId, role, userInfo)));
        }

        return getRoomDetails(roomId);
    }

    @SuppressWarnings("unchecked")
    public void leaveRoom(String roomId, String userId) {
        List<Object> result = redisTemplate.execute(
                leaveRoomScript,
                List.of(RedisKeys.roomInfo(roomId), RedisKeys.presenceUser(userId),
                        RedisKeys.LOBBY_INDEX, RedisKeys.roomSpectators(roomId)),
                userId, roomId);

        if (result == null) throw new RuntimeException("Lua script returned null");

        long code = (Long) result.getFirst();
        String reason = (String) result.get(1);
        String role = (String) result.get(2);
        List<String> others = (List<String>) result.get(3);

        switch ((int) code) {
            case -1 -> throw new NotFoundException("Room not found");
            case -2 -> throw new BadRequestException("Room is not accepting leave requests (not WAITING)");
            case -3 -> throw new ForbiddenException("You are not in this room");
        }

        if ("HOST_LEFT".equals(reason)) {
            // others = white/black IDs + "spectator:{id}" entries
            // Players need presence reset; spectators do not (they were never set IN_ROOM)
            for (String entry : others) {
                if (!entry.startsWith("spectator:")) {
                    setStatusFromInRoomToOnline(entry);
                }
            }
            setStatusFromInRoomToOnline(userId);

            RoomDeletedPayload payload = new RoomDeletedPayload(roomId);
            WsEvent<RoomDeletedPayload> event = new WsEvent<>("ROOM_DELETED", payload);
            messagingTemplate.convertAndSend("/topic/lobbies", event);
            messagingTemplate.convertAndSend("/topic/room/" + roomId, event);
        } else if ("SPECTATOR_LEFT".equals(reason)) {
            messagingTemplate.convertAndSend("/topic/room/" + roomId,
                    new WsEvent<>("PLAYER_LEFT", new PlayerLeftPayload(role, userId)));
        } else {
            setStatusFromInRoomToOnline(userId);
            messagingTemplate.convertAndSend("/topic/room/" + roomId,
                    new WsEvent<>("PLAYER_LEFT", new PlayerLeftPayload(role, userId)));
            messagingTemplate.convertAndSend("/topic/lobbies",
                    new WsEvent<>("ROOM_UPDATED", new RoomUpdatedPayload(roomId, role, null)));
        }
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
            try {
                userIdsToFetch.add(Long.parseLong(spId));
            } catch (NumberFormatException ignored) {
            }
        }

        Map<Long, UserSimpleResponse> users = userIdsToFetch.isEmpty()
                ? Collections.emptyMap()
                : userService.getUsersSimpleResponseByIds(userIdsToFetch);

        List<UserSimpleResponse> spectators = spectatorIds.stream()
                .map(id -> {
                    try {
                        return users.get(Long.parseLong(id));
                    } catch (Exception e) {
                        return null;
                    }
                })
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

    // ponytail: Lua DELs the presence key then this re-creates it with only {status:ONLINE}.
    // Intentional reset — loses any TTL on the old key. Upgrade: pass field values back from
    // Lua and do an atomic HSET+EXPIRE in one script if TTL becomes relevant.
    private void setStatusFromInRoomToOnline(String userId) {
        redisTemplate.execute(
                presenceSetStatusScript,
                List.of(RedisKeys.presenceUser(userId)),
                "ONLINE", "roomId", "isHost", "role"
        );
    }

    // ── Disconnect cleanup ──────────────────────────────────────────────────────

    @EventListener
    public void onUserWentOffline(UserWentOfflineEvent event) {
        if (!PresenceStatus.IN_ROOM.name().equals(event.presenceData().getOrDefault("status", ""))) return;

        String roomId = event.presenceData().get("roomId");
        if (roomId == null) return;

        if ("true".equals(event.presenceData().get("is_host"))) {
            cleanupRoomOnDisconnect(roomId, event.userId());
        } else {
            cleanupSeatOnDisconnect(roomId, event.userId());
        }
    }

    @SuppressWarnings("unchecked")
    private void cleanupRoomOnDisconnect(String roomId, String userId) {
        // DEL on host presence key is a Redis NOP here — already cleaned by presence_disconnect.lua
        List<Object> result = redisTemplate.execute(
                leaveRoomScript,
                List.of(RedisKeys.roomInfo(roomId), RedisKeys.presenceUser(userId),
                        RedisKeys.LOBBY_INDEX, RedisKeys.roomSpectators(roomId)),
                userId, roomId
        );
        if (result == null || (Long) result.getFirst() < 0) {
            log.warn("Room {} already gone when host {} disconnected", roomId, userId);
            return;
        }
        List<String> others = (List<String>) result.get(3);
        for (String entry : others) {
            if (!entry.startsWith("spectator:")) setStatusFromInRoomToOnline(entry);
        }
        WsEvent<RoomDeletedPayload> ev = new WsEvent<>("ROOM_DELETED", new RoomDeletedPayload(roomId));
        messagingTemplate.convertAndSend("/topic/lobbies", ev);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, ev);
        log.debug("Room {} cleaned up after host {} disconnected", roomId, userId);
    }

    @SuppressWarnings("unchecked")
    private void cleanupSeatOnDisconnect(String roomId, String userId) {
        List<Object> result = redisTemplate.execute(
                leaveRoomScript,
                List.of(RedisKeys.roomInfo(roomId), RedisKeys.presenceUser(userId),
                        RedisKeys.LOBBY_INDEX, RedisKeys.roomSpectators(roomId)),
                userId, roomId
        );
        if (result == null || (Long) result.getFirst() < 0) {
            log.warn("Could not clean up seat for disconnected player {} in room {}", userId, roomId);
            return;
        }
        String role = (String) result.get(2);
        messagingTemplate.convertAndSend("/topic/room/" + roomId,
                new WsEvent<>("PLAYER_LEFT", new PlayerLeftPayload(role, userId)));
        messagingTemplate.convertAndSend("/topic/lobbies",
                new WsEvent<>("ROOM_UPDATED", new RoomUpdatedPayload(roomId, role, null)));
    }
}
