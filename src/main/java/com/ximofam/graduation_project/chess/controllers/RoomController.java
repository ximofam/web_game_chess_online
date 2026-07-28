package com.ximofam.graduation_project.chess.controllers;

import com.ximofam.graduation_project.chess.dtos.request.ChatSendRequest;
import com.ximofam.graduation_project.chess.dtos.request.CreateRoomRequest;
import com.ximofam.graduation_project.chess.dtos.request.JoinRoomRequest;
import com.ximofam.graduation_project.chess.dtos.response.RoomResponse;
import com.ximofam.graduation_project.chess.services.RoomService;
import com.ximofam.graduation_project.common.exceptions.http.BadRequestException;
import com.ximofam.graduation_project.common.exceptions.http.ForbiddenException;
import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.common.utils.AuthUtils;
import com.ximofam.graduation_project.common.ws.ChatMessagePayload;
import com.ximofam.graduation_project.users.services.PresenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final PresenceService presenceService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid CreateRoomRequest request) {

        String userIdStr = userId.toString();
        if (!presenceService.isOnline(userIdStr)) {
            throw new ForbiddenException("You must be online to create a room.");
        }

        if (presenceService.isInRoom(userIdStr)) {
            throw new BadRequestException("You are already in a room.");
        }

        return ResponseEntity.ok(roomService.createRoom(userIdStr, request));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<RoomResponse> joinRoom(
            @AuthenticationPrincipal Long userId,
            @PathVariable String roomId,
            @RequestBody(required = false) @Valid JoinRoomRequest request) {

        String userIdStr = userId.toString();
        if (!presenceService.isOnline(userIdStr)) {
            throw new ForbiddenException("You must be online to join a room.");
        }
        if (presenceService.isInRoom(userIdStr)) {
            throw new BadRequestException("You are already in a room.");
        }

        if (request == null) request = new JoinRoomRequest(); // default role = black
        return ResponseEntity.ok(roomService.joinRoom(roomId, userIdStr, request));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<?> leaveRoom(@AuthenticationPrincipal Long userId,
                                       @PathVariable String roomId) {

        String userIdStr = userId.toString();
        if (!presenceService.isOnline(userIdStr)) {
            throw new ForbiddenException("You must be online to leave a room.");
        }

        roomService.leaveRoom(roomId, userIdStr);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getLobbyRooms(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(roomService.getLobbyRooms(q, page, size));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getRoomDetails(
            @AuthenticationPrincipal Long userId,
            @PathVariable String roomId) {

        RoomResponse room = roomService.getRoomDetails(roomId);
        if (room == null) throw new NotFoundException("Room not found.");

        if (room.getSettings() != null && room.getSettings().isPrivate()) {
            if (!roomService.isMember(roomId, userId.toString())) {
                throw new ForbiddenException("This is a private room. You are not allowed to view it.");
            }
        }

        return ResponseEntity.ok(room);
    }

    @MessageMapping("/room.{roomId}.chat")
    public void sendChatMessage(@DestinationVariable String roomId,
                                @Valid @Payload ChatSendRequest request,
                                SimpMessageHeaderAccessor accessor) {
        String userId = AuthUtils.resolveUserId(accessor.getUser());
        if (userId == null) return;

        roomService.sendChatMessage(roomId, userId, request.getMessage().strip());
    }

    @GetMapping("/{roomId}/chat")
    public ResponseEntity<List<ChatMessagePayload>> getChatHistory(@PathVariable String roomId) {
        return ResponseEntity.ok(roomService.getChatHistory(roomId));
    }
}
