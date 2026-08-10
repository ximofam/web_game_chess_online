package com.ximofam.graduation_project.chess.controllers;

import com.ximofam.graduation_project.chess.dtos.request.ChatSendRequest;
import com.ximofam.graduation_project.chess.dtos.request.CreateRoomRequest;
import com.ximofam.graduation_project.chess.dtos.request.JoinRoomRequest;
import com.ximofam.graduation_project.chess.dtos.response.RoomDetailResponse;
import com.ximofam.graduation_project.chess.dtos.response.RoomResponse;
import com.ximofam.graduation_project.chess.dtos.ws.ChatMessagePayload;
import com.ximofam.graduation_project.chess.enums.LeaveReason;
import com.ximofam.graduation_project.chess.services.RoomService;
import com.ximofam.graduation_project.common.exceptions.http.ForbiddenException;
import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.common.utils.AuthUtils;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final PresenceService presenceService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @AuthenticationPrincipal UUID userId,
            @RequestBody @Valid CreateRoomRequest request) {

        String userIdStr = userId.toString();
        return ResponseEntity.ok(roomService.createRoom(userIdStr, request));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<RoomDetailResponse> joinRoom(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String roomId,
            @RequestBody(required = false) @Valid JoinRoomRequest request) {

        String userIdStr = userId.toString();
        if (request == null) request = new JoinRoomRequest();
        return ResponseEntity.ok(roomService.joinRoom(roomId, userIdStr, request));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(@AuthenticationPrincipal UUID userId,
                                          @PathVariable String roomId) {

        String userIdStr = userId.toString();
        if (!presenceService.isOnline(userIdStr)) {
            throw new ForbiddenException("You must be online to leave a room.");
        }

        roomService.leaveRoom(roomId, userIdStr, LeaveReason.USER_LEAVE);
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
    public ResponseEntity<RoomDetailResponse> getRoomDetails(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String roomId) {

        RoomDetailResponse room = roomService.getRoomDetails(roomId);
        if (room == null) throw new NotFoundException("Room not found.");

        if (room.getSettings() != null && room.getSettings().isPrivate() && !roomService.isMember(roomId, userId.toString())) {
            throw new ForbiddenException("This is a private room. You are not allowed to view it.");
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
