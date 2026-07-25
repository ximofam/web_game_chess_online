package com.ximofam.graduation_project.chess.controllers;

import com.ximofam.graduation_project.chess.dtos.request.CreateRoomRequest;
import com.ximofam.graduation_project.chess.dtos.response.RoomResponse;
import com.ximofam.graduation_project.chess.services.RoomService;
import com.ximofam.graduation_project.common.exceptions.http.BadRequestException;
import com.ximofam.graduation_project.common.exceptions.http.ForbiddenException;
import com.ximofam.graduation_project.users.services.PresenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final PresenceService presenceService;

    @PostMapping
    public ResponseEntity<?> createRoom(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid CreateRoomRequest request) {

        String userIdStr = userId.toString();
        if (!presenceService.isOnline(userIdStr)) {
            throw new ForbiddenException("You must be online to create a room.");
        }

        if (presenceService.isInRoom(userIdStr)) {
            throw new BadRequestException("You are already in a room.");
        }

        String roomId = roomService.createRoom(userIdStr, request);
        return ResponseEntity.ok(Map.of("roomId", roomId));
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
        if (room == null) throw new BadRequestException("Room not found.");

        if (room.getSettings() != null && room.getSettings().isPrivate()) {
            if (!roomService.isMember(roomId, userId.toString())) {
                throw new ForbiddenException("This is a private room. You are not allowed to view it.");
            }
        }

        return ResponseEntity.ok(room);
    }
}
