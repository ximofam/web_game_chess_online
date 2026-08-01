package com.ximofam.graduation_project.chess.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ximofam.graduation_project.chess.dtos.request.CreateRoomRequest;
import com.ximofam.graduation_project.chess.dtos.request.JoinRoomRequest;
import com.ximofam.graduation_project.chess.dtos.response.RoomResponse;
import com.ximofam.graduation_project.chess.dtos.ws.*;
import com.ximofam.graduation_project.chess.enums.LeaveReason;
import com.ximofam.graduation_project.chess.enums.PlayerRole;
import com.ximofam.graduation_project.chess.enums.RoomStatus;
import com.ximofam.graduation_project.chess.mappers.RoomMapper;
import com.ximofam.graduation_project.common.exceptions.http.ForbiddenException;
import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.common.helpers.dtos.WsEvent;
import com.ximofam.graduation_project.common.utils.LuaErrorHandler;
import com.ximofam.graduation_project.common.utils.RedisKeys;
import com.ximofam.graduation_project.common.utils.TopicUtils;
import com.ximofam.graduation_project.common.utils.Utils;
import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import com.ximofam.graduation_project.users.enums.PresenceStatus;
import com.ximofam.graduation_project.users.services.PresenceService;
import com.ximofam.graduation_project.users.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {
    private final StringRedisTemplate redisTemplate;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisScript<Long> createRoomScript;
    private final RedisScript<List<Object>> searchLobbyScript;
    private final RedisScript<Long> joinRoomScript;
    private final RedisScript<List<Object>> leaveRoomScript;
    private final RedisScript<List<Object>> deleteRoomScript;
    private final PresenceService presenceService;
    private final ObjectMapper objectMapper;
    private final RoomMapper roomMapper;

    private static final int CHAT_HISTORY_SIZE = 10;

    public RoomResponse createRoom(String hostId, CreateRoomRequest request) {
        String roomId = UUID.randomUUID().toString();
        String roomKey = RedisKeys.roomInfo(roomId);
        String userPresenceKey = RedisKeys.presenceUser(hostId);
        long createdAt = Instant.now().toEpochMilli();
        UserSimpleResponse hostInfo = userService.getUserSimpleResponseById(UUID.fromString(hostId));
        String settingsJson = roomMapper.writeSettings(request.getSettings());

        Long result = redisTemplate.execute(
                createRoomScript,
                List.of(roomKey, RedisKeys.LOBBY_INDEX, userPresenceKey),
                hostId,
                settingsJson,
                String.valueOf(createdAt),
                roomId,
                request.getName()
        );

        if (result == null) throw new NotFoundException("Lua script returned null");

        int luaCode = result.intValue();
        LuaErrorHandler.handle(luaCode);

        RoomResponse roomData = new RoomResponse();
        roomData.setRoomId(roomId);
        roomData.setName(request.getName());
        roomData.setStatus(RoomStatus.WAITING.name());
        roomData.setHostId(hostId);
        roomData.setHost(hostInfo);
        roomData.setWhite(hostInfo);
        roomData.setBlack(null);
        roomData.setSpectators(List.of());
        roomData.setCreatedAt(createdAt);
        roomData.setSettings(request.getSettings());

        messagingTemplate.convertAndSend(TopicUtils.LOBBIES, new WsEvent<>("ROOM_CREATED", roomData));
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
        Set<UUID> userIdsToFetch = new HashSet<>();
        for (int i = 0; i < roomIds.size(); i++) {
            Map<Object, Object> raw = (Map<Object, Object>) pipelinedResults.get(i);
            if (raw == null || raw.isEmpty()) continue;

            rawByRoomId.put(roomIds.get(i), raw);
            collectParticipantIds(raw, userIdsToFetch);
        }

        Map<UUID, UserSimpleResponse> hydratedUsers = userIdsToFetch.isEmpty()
                ? Collections.emptyMap()
                : userService.getUsersSimpleResponseByIds(userIdsToFetch);

        List<RoomResponse> content = new ArrayList<>(rawByRoomId.size());
        for (Map.Entry<String, Map<Object, Object>> entry : rawByRoomId.entrySet()) {
            content.add(roomMapper.buildRoomResponse(entry.getKey(), entry.getValue(), List.of(), hydratedUsers));
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

    public RoomResponse joinRoom(String roomId, String userId, JoinRoomRequest request) {
        String role = request.getRole();
        long now = Instant.now().toEpochMilli();

        Long result = redisTemplate.execute(
                joinRoomScript,
                List.of(RedisKeys.roomInfo(roomId), RedisKeys.presenceUser(userId), RedisKeys.roomSpectators(roomId)),
                userId, roomId, role, String.valueOf(now)
        );

        if (result == null) throw new NotFoundException("Lua script returned null");

        int luaCode = result.intValue();
        LuaErrorHandler.handle(luaCode, role);

        UserSimpleResponse userInfo = userService.getUserSimpleResponseById(UUID.fromString(userId));
        messagingTemplate.convertAndSend(TopicUtils.room(roomId),
                new WsEvent<>("PLAYER_JOINED", new PlayerJoinedPayload(role, userInfo)));

        if (!"spectator".equals(role)) {
            messagingTemplate.convertAndSend(TopicUtils.LOBBIES,
                    new WsEvent<>("ROOM_UPDATED", new RoomUpdatedPayload(roomId, role, userInfo)));
        }

        return getRoomDetails(roomId);
    }

    public void leaveRoom(String roomId, String userId, LeaveReason leaveReason) {
        List<Object> result = redisTemplate.execute(leaveRoomScript,
                List.of(RedisKeys.roomInfo(roomId), RedisKeys.roomSpectators(roomId)), userId);

        if (result == null) throw new NotFoundException("Lua script returned null");

        long code = ((Number) result.get(0)).longValue();
        LuaErrorHandler.handle((int) code);

        String reason = (String) result.get(1);
        String role = result.size() > 2 ? (String) result.get(2) : null;

        if (leaveReason.equals(LeaveReason.USER_LEAVE)) {
            presenceService.setPresenceStatus(userId, PresenceStatus.ONLINE, "is_host", "roomId", "role");
        }

        if ("HOST_LEFT".equals(reason)) {
            List<Object> delRoomRes = redisTemplate.execute(
                    deleteRoomScript,
                    List.of(RedisKeys.roomInfo(roomId), RedisKeys.LOBBY_INDEX,
                            RedisKeys.roomSpectators(roomId), RedisKeys.roomChat(roomId)),
                    roomId);

            if (delRoomRes != null) {
                for (Object obj : delRoomRes) {
                    presenceService.setPresenceStatus(String.valueOf(obj), PresenceStatus.ONLINE, "is_host", "roomId", "role");
                }
            }

            RoomDeletedPayload payload = new RoomDeletedPayload(roomId);
            WsEvent<RoomDeletedPayload> event = new WsEvent<>("ROOM_DELETED", payload);
            messagingTemplate.convertAndSend(TopicUtils.LOBBIES, event);
            messagingTemplate.convertAndSend(TopicUtils.room(roomId), event);
        } else if ("SPECTATOR_LEFT".equals(reason)) {
            messagingTemplate.convertAndSend(TopicUtils.room(roomId),
                    new WsEvent<>("PLAYER_LEFT", new PlayerLeftPayload(role, userId)));
        } else {
            messagingTemplate.convertAndSend(TopicUtils.room(roomId),
                    new WsEvent<>("PLAYER_LEFT", new PlayerLeftPayload(role, userId)));
            messagingTemplate.convertAndSend(TopicUtils.LOBBIES,
                    new WsEvent<>("ROOM_UPDATED", new RoomUpdatedPayload(roomId, role, null)));
        }
    }

    public void sendChatMessage(String roomId, String userId, String message) {
        Object settingsRaw = redisTemplate.opsForHash().get(RedisKeys.roomInfo(roomId), "settings");
        if (settingsRaw == null) throw new NotFoundException("Room not found");

        if (roomMapper.parseSettings(settingsRaw).isChatLocked()) {
            throw new ForbiddenException("Chat is locked in this room");
        }

        UserSimpleResponse sender = userService.getUserSimpleResponseById(UUID.fromString(userId));
        ChatMessagePayload payload = new ChatMessagePayload(sender, message, Instant.now().toEpochMilli());

        String chatKey = RedisKeys.roomChat(roomId);
        redisTemplate.opsForList().leftPush(chatKey, Utils.writeJson(objectMapper, payload));
        redisTemplate.opsForList().trim(chatKey, 0, CHAT_HISTORY_SIZE - 1L);

        messagingTemplate.convertAndSend(TopicUtils.room(roomId), new WsEvent<>("CHAT_MESSAGE", payload));
    }

    public List<ChatMessagePayload> getChatHistory(String roomId) {
        List<String> raw = redisTemplate.opsForList().range(RedisKeys.roomChat(roomId), 0, -1);
        if (raw == null || raw.isEmpty()) return List.of();

        List<ChatMessagePayload> messages = raw.stream()
                .map(json -> Utils.parseJson(objectMapper, json, ChatMessagePayload.class))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Collections.reverse(messages);
        return messages;
    }

    public boolean isMember(String roomId, String userId) {
        String roomKey = RedisKeys.roomInfo(roomId);
        List<Object> userIds = redisTemplate.opsForHash().multiGet(roomKey,
                Arrays.asList("hostId", "whiteId", "blackId"));
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

        Set<UUID> userIdsToFetch = new HashSet<>();
        collectParticipantIds(raw, userIdsToFetch);
        for (String spId : spectatorIds) {
            try {
                userIdsToFetch.add(UUID.fromString(spId));
            } catch (IllegalArgumentException ignored) { // NOSONAR java:S108
            }
        }

        Map<UUID, UserSimpleResponse> users = userIdsToFetch.isEmpty()
                ? Collections.emptyMap()
                : userService.getUsersSimpleResponseByIds(userIdsToFetch);

        List<UserSimpleResponse> spectators = spectatorIds.stream()
                .map(id -> roomMapper.resolveUser(id, users))
                .filter(Objects::nonNull)
                .toList();

        return roomMapper.buildRoomResponse(roomId, raw, spectators, users);
    }

    private static void collectParticipantIds(Map<Object, Object> raw, Set<UUID> target) {
        for (String role : new String[]{"hostId", "whiteId", "blackId"}) {
            String id = Utils.str(raw, role);
            if (id != null && !id.isEmpty()) {
                try {
                    target.add(UUID.fromString(id));
                } catch (IllegalArgumentException ignored) { // NOSONAR java:S108
                }
            }
        }
    }
}
