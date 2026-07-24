package com.ximofam.graduation_project.chess.controllers;

import com.ximofam.graduation_project.chess.dtos.request.CreateRoomRequest;
import com.ximofam.graduation_project.chess.services.RoomService;
import com.ximofam.graduation_project.users.services.PresenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You must be online to create a room."));
        }

        String roomId = roomService.createRoom(userIdStr, request);
        return ResponseEntity.ok(Map.of("roomId", roomId));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getLobbyRooms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(roomService.getLobbyRooms(page, size));
    }
}
